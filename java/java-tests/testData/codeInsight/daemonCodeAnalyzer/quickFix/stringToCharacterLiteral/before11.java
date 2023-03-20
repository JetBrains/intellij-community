// "Change "\'" to '\'' (to char literal)" "true-preview"
class Quotes {
    void m1(char ch) {}

    void test() {
        m1(<caret>"\'");
    }
}
