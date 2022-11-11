// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f() {
        int n;
        {
            n = 1;
            System.out.println();
            return n;
        }
    }
}