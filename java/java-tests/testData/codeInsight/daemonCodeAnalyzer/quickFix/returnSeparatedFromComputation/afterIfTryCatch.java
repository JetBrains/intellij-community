// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(boolean b, boolean c) {
        int n = -1;
        if (b) {
            try {
                return g();
            }
            catch (RuntimeException e) {
                d(e);
            }
        }
        else {
            return 2;
        }
        return n;
    }

    int g() {
        return 1;
    }

    void d(Exception e) {
        e.printStackTrace()
    }
}