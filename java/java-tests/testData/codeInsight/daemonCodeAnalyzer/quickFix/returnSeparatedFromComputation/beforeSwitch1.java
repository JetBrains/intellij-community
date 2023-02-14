// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(int a) {
        int n = -1;
        switch (a) {
            case 1:
                n = 2;
                break;
            case 2:
                n = 4;
                break;
        }
        r<caret>eturn n;
    }
}