// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(int a) {
        int n = -1;
        switch (a) {
            case 0:
            case 1:
            case 2:
                return n;
            case 10:
            case 20:
                return n + 1;
            case 30:
            case 40:
            case 50:
                return 2;
            case 90:
                return 3;
            default:
                throw new IllegalArgumentException();
        }
    }
}