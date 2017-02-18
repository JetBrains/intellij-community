// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(boolean b) {
        int n = 0;
        if (b) System.out.println("yes");
        else n = 2;
        r<caret>eturn n;
    }
}