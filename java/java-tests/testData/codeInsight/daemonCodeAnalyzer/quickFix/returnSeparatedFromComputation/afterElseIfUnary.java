// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(int k) {
        if (k == 1)
            return 1;
        else if (k == 2)
            return 2;
        return -1;
    }
}