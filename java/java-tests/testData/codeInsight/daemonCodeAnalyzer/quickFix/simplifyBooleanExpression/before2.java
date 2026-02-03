// "Simplify boolean expression" "true-preview"
class X {
    void f() {
        if (false || (<caret>true && !(false))|| this == null) return;
    }
}