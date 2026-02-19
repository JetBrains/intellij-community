// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(boolean b) {
        int n = -1;
        try {
            n = 1;
            if (b) {
                throw new RuntimeException();
            }
        }
        catch (RuntimeException e) {
            return 2;
        }
        re<caret>turn n;
    }
}