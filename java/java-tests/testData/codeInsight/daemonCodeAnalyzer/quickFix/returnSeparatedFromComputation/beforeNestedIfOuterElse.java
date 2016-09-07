// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(boolean a, boolean b) {
        int n = -1;
        if (a) {
            if (b) n = 1;
        }
        else n = 2;
        re<caret>turn n;
    }
}