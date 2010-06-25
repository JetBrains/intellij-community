// "Change "1" to '1' (to char literal)" "true"
class Simple extends Ancestor {
    public void m(int i) {

    }

    public void test() {
        m(<caret>'1');
    }
}

class Ancestor {
    public void m(char ch) {

    }
}
