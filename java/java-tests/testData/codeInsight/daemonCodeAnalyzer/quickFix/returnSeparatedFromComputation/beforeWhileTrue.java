// "Move 'return' closer to computation of the value of 'r'" "true"
class T {
    long f() {
        long r;
        long s = System.currentTimeMillis();
        long t = s;
        while (true) {
            t = System.currentTimeMillis();
            if (t - s > 100) {
                r = t;
                break;
            }
        }
        retu<caret>rn r;
    }
}