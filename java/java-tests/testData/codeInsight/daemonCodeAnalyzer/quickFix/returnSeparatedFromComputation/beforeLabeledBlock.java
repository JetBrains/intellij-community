// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(boolean b) {
        int n;
        myLabel:
        {
            n = 1;
            if (b) break myLabel;
            n = 2;
        }
        ret<caret>urn n;
    }
}