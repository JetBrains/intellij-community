// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f() {
        for(int i=0;; i++) {
            if (i % 127 == 0 && i % 129 == 0) {
                return i + 1;
            }
        }
    }
}