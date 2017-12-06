// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(int k) {
        if (k == 1)
            return 1;
        return /*comment1*/ -1;
    }
}