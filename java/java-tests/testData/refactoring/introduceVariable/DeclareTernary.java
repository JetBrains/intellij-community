class Test {
    void test(String s) {
        final String x = s == null ? "" : <selection>s.trim()</selection>.substring(1), y = x.replace("foo", "bar");
        System.out.println(x);
    }
}
