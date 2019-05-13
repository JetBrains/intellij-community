// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(int k) {
        if (k == 1)
            return 1;
        else if (k == 2)
            return /*comment1*/ -1;
        else if (k == 3)
            return 3;
        return -1;
    }
}