// "Change "1" to '1' (to char literal)" "true-preview"
class Simple {
    public void m1(final int i, final char ch) {}

    public void test() {
        m1("does not matter", (('1')));
    }
}
