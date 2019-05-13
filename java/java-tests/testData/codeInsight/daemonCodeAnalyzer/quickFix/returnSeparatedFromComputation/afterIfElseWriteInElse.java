// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(boolean b) {
        if (b) System.out.println("yes");
        else return 2;
        return 0;
    }
}