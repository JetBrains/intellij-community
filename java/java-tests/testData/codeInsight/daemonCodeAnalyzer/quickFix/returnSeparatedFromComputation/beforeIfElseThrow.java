// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(boolean b) {
        int n = -1;
        if (b) {
            n = 1;
        }
        else {
            throw new RuntimeException();
        }
        r<caret>eturn n;
    }
}