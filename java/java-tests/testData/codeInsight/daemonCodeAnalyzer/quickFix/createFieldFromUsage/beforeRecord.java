// "Create field 'foo'" "false"
record R() {
    void test() {
        System.out.println(f<caret>oo);
    }
}