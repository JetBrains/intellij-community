// "Unwrap 'if' statement" "true-preview"
class X {
    void f() {
        <caret>{
            int i = 0;
        }
        int i = 0;
    }
}