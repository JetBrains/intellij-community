// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(int a) {
        int n = -1;
        while (true) {
            n = g();
            if(n == 0) continue;
            break;
        }
        ret<caret>urn n;
    }

    int g() {
        return 1;
    }
}