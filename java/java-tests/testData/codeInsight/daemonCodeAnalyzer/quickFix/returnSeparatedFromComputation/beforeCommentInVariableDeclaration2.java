// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(int k) {
        int n = /*comment1*/-1;
        if (k == 1)
            n = 1;
        else if (k == 2)
            return n;
        else if (k == 3)
            return 3;
        <caret>return n;
    }
}