// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(boolean b) {
        int n = 0;
        myLabel:
        if (b) n = 1;
        else break myLabel;
        ret<caret>urn n;
    }
}