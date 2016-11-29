// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(boolean b) {
        int n;
        myLabel:
        {
            n = 1;
            if (b) return n;
            return 2;
        }
    }
}