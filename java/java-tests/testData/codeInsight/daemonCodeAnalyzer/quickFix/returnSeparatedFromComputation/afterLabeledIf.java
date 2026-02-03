// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(boolean b) {
        myLabel:
        if (b) return 1;
        else return 0;
    }
}