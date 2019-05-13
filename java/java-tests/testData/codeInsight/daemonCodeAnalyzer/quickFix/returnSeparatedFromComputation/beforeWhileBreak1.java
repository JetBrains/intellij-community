// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(int a) {
        int n = -1;
        while (true) {
            n = g();
            if(n != 0) break;
        }
        ret<caret>urn n;
    }

    int g() {
        return 1;
    }
}