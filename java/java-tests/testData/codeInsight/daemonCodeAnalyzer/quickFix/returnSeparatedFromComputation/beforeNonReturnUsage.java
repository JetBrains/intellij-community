// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int foo(int k) {
        int n = k;
        if (n < 0)
            n = -1;
        <caret>return n;
    }
}