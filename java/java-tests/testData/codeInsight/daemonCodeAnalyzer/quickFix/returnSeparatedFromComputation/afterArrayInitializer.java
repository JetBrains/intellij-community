// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int[] f(int k) {
        int[] n = {-1};
        if (k == 1)
            return new int[]{1};
        return n;
    }
}