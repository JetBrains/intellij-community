// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(int[] a) {
        int n = -1;
        myLabel:
        for (int i = 0; i < a.length; i++) {
            n = i;
            if (a[0] == 0) break myLabel;
        }
        re<caret>turn n;
    }
}
