// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(boolean b) {
        if (b) {
            return 1;
        }
        else {
            throw new RuntimeException();
        }
    }
}