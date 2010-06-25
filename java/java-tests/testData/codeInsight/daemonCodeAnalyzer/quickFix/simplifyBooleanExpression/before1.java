// "Simplify boolean expression" "true"
class X {
    void f() {
        if (false || <caret>true || this == null) return;
    }
}