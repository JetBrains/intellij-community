// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(int a) {
        int t = a;
        while (t != null) {
            if (t == 1) {
                return 10;
            }
            else if (t == 2) {
                return 20;
            }
            else {
                t = t + 1;
                continue;
            }
        }
        return -1;
    }
}