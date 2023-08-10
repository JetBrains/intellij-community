// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(int k) {
        if (k == 1)
            return 1;
        return /*comment1*/ -1;
    }
}