// "Change '"' to "\"" (to String literal)" "true-preview"
class Quotes {
    void m1(String s) {}

    void test() {
        m1(<caret>'"');
    }
}
