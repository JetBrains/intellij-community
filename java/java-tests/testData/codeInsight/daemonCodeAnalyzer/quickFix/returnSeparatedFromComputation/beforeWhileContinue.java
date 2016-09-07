// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(int a) {
        int n = -1;
        int t = a;
        while (t != null) {
            if (t == 1) {
                n = 10;
            }
            else if (t == 2) {
                n = 20;
            }
            else {
                t = t + 1;
                continue;
            }
            break;
        }
        ret<caret>urn n;
    }
}