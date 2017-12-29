// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(int[] a) {
        myLabel:
        for (int i = 0; i < a.length; i++) {
            if (a[0] == 0) {
                return i;
            }
        }
        return -1;
    }
}
