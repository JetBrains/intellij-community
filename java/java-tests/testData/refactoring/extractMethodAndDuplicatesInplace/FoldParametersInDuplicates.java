class Test {

    void test(String[] args) {
        String x = "one";
        String y = "two";
        String sample = "sample string";
        <selection>checkString(args[0], sample);</selection>
        System.out.println();
        checkString(args[1], sample);
        System.out.println();
        checkString("one", sample);
        checkString("two", sample);
    }

    boolean checkString(String one, String two) {
        return one.equals(two);
    }
}