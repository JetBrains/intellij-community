// "Bind constructor parameters to fields" "true-preview"
class BindField {
    private final String myAgent;
    private final Integer myInstrumentAgent;

    public BindField(String agent, Integer instrumentAgent) {

        myAgent = agent;
        myInstrumentAgent = instrumentAgent;
    }
}