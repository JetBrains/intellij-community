// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    final int k = 1;

    int foo() {
        if (k < 0)
            return -1;
        return k;
    }
}