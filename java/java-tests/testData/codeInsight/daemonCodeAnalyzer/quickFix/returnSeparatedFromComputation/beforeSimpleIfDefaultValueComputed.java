// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(boolean b) {
        int n = g();
        if (b) n = 1;
        re<caret>turn n;
    }
    int g() {
        return 0;
    }
}