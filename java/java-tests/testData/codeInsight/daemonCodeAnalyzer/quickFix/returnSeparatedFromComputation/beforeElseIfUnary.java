// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(int k) {
        int n = -1;
        if (k == 1)
            n = 1;
        else if (k == 2)
            n = 2;
        re<caret>turn n;
    }
}