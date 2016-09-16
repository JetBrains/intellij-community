// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(int a) {
        int n = -1;
        switch (a) {
            case 0:
            case 1:
            case 2:
                break;
            case 10:
            case 20:
                n = n + 1;
                break;
            case 30:
            case 40:
            case 50:
                n = 2;
                break;
            case 90:
                n = 3;
                break;
            default:
                throw new IllegalArgumentException();
        }
        r<caret>eturn n;
    }
}