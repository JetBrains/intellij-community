// "Simplify boolean expression" "false"
class X {
    void f(int a, int b, int c) {
        if ( b - a == b - c - <caret>== b) return;
    }
}
