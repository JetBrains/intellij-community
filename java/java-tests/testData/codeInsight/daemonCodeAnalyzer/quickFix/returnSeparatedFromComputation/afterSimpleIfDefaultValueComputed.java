// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(boolean b) {
        int n = g();
        if (b) return 1;
        return n;
    }
    int g() {
        return 0;
    }
}