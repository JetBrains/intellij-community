// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f() {
        int n;
        {
            n = 1;
            System.out.println();
        }
        re<caret>turn n;
    }
}