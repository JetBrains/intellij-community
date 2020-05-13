record A(int value) {

}

class B {
    void test() {
        new <caret>A(12);
    }
}