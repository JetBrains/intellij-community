// "Move 'return' to computation of the value of 'n'" "true"
class T {
    int f() {
        int n;
        {
            n = 1;
        }
        ret<caret>urn n;
    }
}