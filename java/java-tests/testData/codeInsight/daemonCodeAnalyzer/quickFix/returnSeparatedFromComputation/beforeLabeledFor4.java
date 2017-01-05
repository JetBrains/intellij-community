// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(int[][] a) {
        int n = -1;
        myLabel:
        for (int i = 0; i < a.length; i++) {
            if (a[i].length == 0) {
                n = -i - 1;
                break myLabel;
            }
            for(int j = 0; j < a[i].length; j++) {
                n = j;
                if (a[i][j] == 0) break myLabel;
            }
        }
        re<caret>turn n;
    }
}
