// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(int[] a, int b) {
        int n = -1;
        for (int i = 0; i < a.length; i++) {
            if (a[i] == b) {
                n = i;
                break;
            }
        }
        r<caret>eturn n;
    }
}