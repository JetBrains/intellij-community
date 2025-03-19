// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(int a) {
        switch (a) {
            case 1:
                return 2;
            case 2:
                return 4;
            default:
                return 0;
            case 3:
                return 10;
        }
    }
}