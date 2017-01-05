// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(boolean b, boolean c) {
        int n = -1;
        if (b) {
            try {
                n = g();
            }
            catch (RuntimeException e) {
                d(e);
            }
        }
        else {
            n = 2;
        }
        r<caret>eturn n;
    }

    int g() {
        return 1;
    }

    void d(Exception e) {
        e.printStackTrace()
    }
}