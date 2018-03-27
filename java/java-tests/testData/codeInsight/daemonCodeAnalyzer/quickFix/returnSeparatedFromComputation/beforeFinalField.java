// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    final int k = 1;

    int foo() {
        int n = k;
        if (k < 0)
            n = -1;
        <caret>return n;
    }
}