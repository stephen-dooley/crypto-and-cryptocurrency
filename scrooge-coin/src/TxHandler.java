import java.security.PublicKey;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TxHandler {

    public UTXOPool publicLedger;
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
    	this.publicLedger = new UTXOPool(utxoPool);
    }

    /**********************************************************************/

    /*
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
    	if (inputsAreInCurrentPool(tx) == false) { // (1)
    		return false;
    	}

    	if (inputsHaveValidSignatures(tx) == false) { // (2)
    		return false;
    	}

    	if (utxosAreNotClaimedMultipleTimes(tx) == false) { // (3)
    		return false;
    	}

    	if (outputsAreNonNegative(tx) == false) { // (4)
    		return false;
    	}

    	if (getTransactionFee(tx) < 0) { // (5)
    		return false;
    	}

    	return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        return Arrays.stream(possibleTxs)
            .filter( tx -> isValidTx(tx))
            .peek(this::updatePoolLedger)
            .toArray(Transaction[]::new);
    }

    private void updatePoolLedger(Transaction tx) {
        //update UTXOPool publicLedger
        //remove all inputs from the publicLedger, since they are not 'spent'
        tx.getInputs().forEach(input -> publicLedger.removeUTXO(new UTXO(input.prevTxHash, input.outputIndex)));

        //add all outputs to the publicLedger, since they are 'unspent'
        IntStream.range(0, tx.numOutputs()).forEach(
            idx -> publicLedger.addUTXO(new UTXO(tx.getHash(), idx), tx.getOutput(idx))
        );

    }


    /************************* (1) *************************/
    // all outputs claimed by {@code tx} are in the current UTXO pool,
    private boolean inputsAreInCurrentPool(Transaction tx) {
        return tx.getInputs().stream() //get inputs
            .map(input ->  new UTXO(input.prevTxHash, input.outputIndex))
            .allMatch(utxo -> publicLedger.contains(utxo));

    }

    /************************* (2) *************************/
    // the signatures on each input of {@code tx} are valid
    private boolean inputsHaveValidSignatures(Transaction tx) {
        return IntStream.range(0, tx.numInputs())
            .allMatch(idx -> {
                Transaction.Input inp = tx.getInput(idx);

                byte[] msg = tx.getRawDataToSign(idx);
                byte[] sig = inp.signature;

                PublicKey pk = publicLedger.getTxOutput(new UTXO(inp.prevTxHash, inp.outputIndex)).address;

                return Crypto.verifySignature(pk, msg, sig);
            });
    }

    /************************* (3) *************************/
    // (3) no UTXO is claimed multiple times by {@code tx}
    private boolean utxosAreNotClaimedMultipleTimes(Transaction tx) {
        // (3) no UTXO is claimed multiple times by {@code tx}
        return tx.getInputs().stream()
            .map(input -> new UTXO(input.prevTxHash, input.outputIndex)) // create UTXO temporaily with tx hash and index
            .collect(Collectors.toSet()) // store the utxo in a set
            //compare set size with actual size, should be same
            //if different then inputs claimed multiple times
            .size() == tx.numInputs();
    }

    /************************* (4) *************************/
    // all of {@code tx}s output values are non-negative, and
    private boolean outputsAreNonNegative(Transaction tx) {
        // (4) all of {@code tx}s output values are non-negative
        return tx.getOutputs().stream()
            .allMatch(o -> o.value>=0); //check if value non-nagtive
    }

    /************************* (5) *************************/
    // the sum of {@code tx}s input values is greater than or equal to the sum of its output
    private double getTransactionFee(Transaction tx) {
        // sum all the inputSum
        double inputSum = tx.getInputs().stream()
            .mapToDouble(input -> publicLedger.getTxOutput(new UTXO(input.prevTxHash, input.outputIndex)).value)
            .sum();
        double outputSum = tx.getOutputs().stream()
            .mapToDouble(output -> output.value)
            .sum();
        return inputSum - outputSum;
    }
}
