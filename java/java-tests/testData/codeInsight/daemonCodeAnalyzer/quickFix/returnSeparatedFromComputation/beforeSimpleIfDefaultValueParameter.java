// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(boolean b, int d) {
        int n = d;
        if (b) n = 1;
        re<caret>turn n;
    }
}