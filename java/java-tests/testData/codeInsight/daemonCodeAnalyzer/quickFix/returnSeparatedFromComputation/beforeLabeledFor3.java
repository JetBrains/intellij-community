// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(int[][] a) {
        int n = -1;
        myLabel:
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                if (a[i][j] == 0) {
                    n = j;
                    break myLabel;
                }
            }
        }
        re<caret>turn n;
    }
}
