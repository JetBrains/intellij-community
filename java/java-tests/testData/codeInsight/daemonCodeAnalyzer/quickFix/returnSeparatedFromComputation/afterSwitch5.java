// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(int a) {
        switch (a) {
            case 1:
                return 2;
            default:
                return 0;
            case 2:
                return 4;
        }
    }
}