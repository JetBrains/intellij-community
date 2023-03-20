// "Move initializer to constructor" "true-preview"
class X {
    final String s;
    final String t;

    X(String s) {
        this.s = s;
        t = "t";
    }
    X() {
        this("s");
    }
}