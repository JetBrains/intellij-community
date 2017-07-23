// "Unwrap 'if' statement" "true"
class X {
    void f() {
        if ((!(!((boolean)true))<caret> ==(true))) {
            //sdf
        }
    }
}