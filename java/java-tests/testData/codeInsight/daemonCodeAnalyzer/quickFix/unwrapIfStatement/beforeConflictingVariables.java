// "Unwrap 'if' statement" "true"
class X {
    void f() {
        if (tru<caret>e) {
            int i = 0;
        }
        int i = 0;
    }
}