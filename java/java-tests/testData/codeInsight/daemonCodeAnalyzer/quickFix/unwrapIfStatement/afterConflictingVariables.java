// "Unwrap 'if' statement" "true"
class X {
    void f() {
        <caret>{
            int i = 0;
        }
        int i = 0;
    }
}