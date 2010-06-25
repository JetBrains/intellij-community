// "Change "1" to '1' (to char literal)" "false"
class Simple {
    public void m(char ch) {

    }

    public void m(String s) {

    }

    public void test() {
        m(<caret>"1");
    }
}
