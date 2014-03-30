// "Move initializer to constructor" "true"
class X {
    final String s;
    final String t = <caret>"t";

    X(String s) {
        this.s = s;
    }
    X() {
        this("s");
    }
}