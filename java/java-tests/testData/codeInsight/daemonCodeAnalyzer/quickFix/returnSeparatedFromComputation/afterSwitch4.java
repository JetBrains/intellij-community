// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(int a) {
        switch (a) {
            case 1:
                return 2;
            case 2:
                return 4;
            default:
                return 0;
            case 0:
        }
        return -1;
    }
}