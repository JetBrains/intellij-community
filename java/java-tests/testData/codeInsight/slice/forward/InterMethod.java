public class T2 {
    int <caret>fff;

    public T2(int i) {
        fff = i;
    }

    public int <flown11>getFff() {
        return <flown1>fff;
    }

    void f(int i2) {
//        fff=i2;
        p(<flown111>getFff());
    }

    public void p(int <flown1111>i) {
        new F(<flown11111>i);
    }

    private class F {
        private int <flown11111111>xss;

        public F(int <flown111111>i) {
            xss = <flown1111111>i;
        }
    }
}
