// "Unwrap 'if' statement" "true-preview"
class X {
    void f() {
        if ((!(!((boolean)true))<caret> ==(true))) {
            //sdf
        }
    }
}