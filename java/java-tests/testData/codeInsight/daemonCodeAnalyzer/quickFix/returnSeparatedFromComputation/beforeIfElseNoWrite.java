// "Move 'return' closer to computation of the value of 'n'" "false"
class T {
    int f(boolean b) {
        int n = 0;
        if (b) System.out.println("yes");
        else System.out.println("no");
        ret<caret>urn n;
    }
}