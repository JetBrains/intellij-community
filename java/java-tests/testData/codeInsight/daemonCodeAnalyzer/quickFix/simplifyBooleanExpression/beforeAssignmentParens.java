// "Simplify boolean expression" "false"
class X {
    void f() {
        boolean x;
        x = (<caret>false); // Another action "Remove redundant parentheses" is ok here
    }
}