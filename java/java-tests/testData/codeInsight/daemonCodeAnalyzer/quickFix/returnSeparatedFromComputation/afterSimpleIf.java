// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(boolean b) {
        if (b) return 1;
        return 0;
    }
}