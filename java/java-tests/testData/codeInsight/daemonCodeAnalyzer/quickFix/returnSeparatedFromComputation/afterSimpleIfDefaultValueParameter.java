// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(boolean b, int d) {
        if (b) return 1;
        return d;
    }
}