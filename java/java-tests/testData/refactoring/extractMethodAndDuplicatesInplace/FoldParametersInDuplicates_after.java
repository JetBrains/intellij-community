class Test {

    void test(String[] args) {
        String x = "one";
        String y = "two";
        String sample = "sample string";
        extracted(args[0], sample);
        System.out.println();
        extracted(args[1], sample);
        System.out.println();
        extracted("one", sample);
        extracted("two", sample);
    }

    private void extracted(String args, String sample) {
        checkString(args, sample);
    }

    boolean checkString(String one, String two) {
        return one.equals(two);
    }
}