// "Create field 'foo'" "true"
record R() {
    private static boolean foo;

    void test() {
        System.out.println(foo);
    }
}