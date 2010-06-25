// "Simplify boolean expression" "true"
class X {
    void f() {
        if (false || (<caret>true && !(false))|| this == null) return;
    }
}