// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(boolean b) {
        int n = 0;
        if (b) n = 1;
        re<caret>turn n;
    }
}