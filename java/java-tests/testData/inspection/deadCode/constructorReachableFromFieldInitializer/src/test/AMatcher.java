package test;

public class AMatcher implements Matcher{
    private static final BracePair []  PAIRS = new BracePair[]{new BracePair()};
    public BracePair[] getPairs() {
        return PAIRS;
    }
}
