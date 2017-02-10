// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f() {
        int n;
        {
            return 1;
        }
    }
}