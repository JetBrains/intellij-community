// "Change "1" to '1' (to char literal)" "true"
class Simple {
    public void m(char ch) {

    }

    public void test() {
        m(<caret>'1');
    }
}
