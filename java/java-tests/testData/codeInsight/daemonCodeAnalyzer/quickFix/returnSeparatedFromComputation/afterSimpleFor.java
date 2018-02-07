// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(int[] a, int b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] == b) {
                return i;
            }
        }
        return -1;
    }
}