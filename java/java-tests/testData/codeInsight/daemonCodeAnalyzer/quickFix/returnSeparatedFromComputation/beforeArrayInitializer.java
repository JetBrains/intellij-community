// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int[] f(int k) {
        int[] n = {-1};
        if (k == 1)
            n = new int[]{1};
        <caret>return n;
    }
}