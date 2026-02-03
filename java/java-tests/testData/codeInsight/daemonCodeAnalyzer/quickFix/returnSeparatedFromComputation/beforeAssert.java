// "Move 'return' closer to computation of the value of 'n'" "false"
class T {
    int f(int a) {
        int n = a;
        assert n != 0;
        r<caret>eturn n;
    }
}