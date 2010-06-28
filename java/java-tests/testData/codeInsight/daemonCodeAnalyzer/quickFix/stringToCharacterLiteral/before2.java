// "Change "1" to '1' (to char literal)" "false"
class Simple {
    public void m(int i, char ch) {

    }

    public void test() {
        m(<caret>"1");
    }
}
