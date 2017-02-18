// "Move 'return' closer to computation of the value of 'n'" "false"
class T {
    int f(int a) {
        int n = -1;
        while (n <= 0) {
            n = g();
            if (n == 0) continue;
            n++;
        }
        ret<caret>urn n;
    }

    int g() {
        return 1;
    }
}