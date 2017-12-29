// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f() {
        {
            return 1;
        }
    }
}