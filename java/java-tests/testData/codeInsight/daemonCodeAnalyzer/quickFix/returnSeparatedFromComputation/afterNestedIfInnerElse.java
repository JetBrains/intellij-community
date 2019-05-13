// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(boolean a, boolean b) {
        if (a) {
            if (b) return 1;
            else return 2;
        }
        return -1;
    }
}