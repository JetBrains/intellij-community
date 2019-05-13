// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int foo(int k) {
        int n = k;
        if (n < 0)
            return -1;
        return n;
    }
}