// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(boolean b) {
        int n = 0;
        myLabel:
        if (b) return 1;
        else return n;
    }
}