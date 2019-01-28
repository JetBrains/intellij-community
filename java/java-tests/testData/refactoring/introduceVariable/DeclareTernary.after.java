class Test {
    void test(String s) {
        final String x;
        if (s == null) {
            x = "";
        } else {
            String temp = s.trim();
            x = temp.substring(1);
        }
        final String y = x.replace("foo", "bar");
        System.out.println(x);
    }
}
