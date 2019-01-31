class Test {
    private static final String STRING;

    static {
        String temp = "foo".trim();
        STRING = temp + temp;
    }
}
