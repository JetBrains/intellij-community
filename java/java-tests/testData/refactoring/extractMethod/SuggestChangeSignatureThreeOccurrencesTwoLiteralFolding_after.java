public class C {
    void second() {
        newMethod(0, 1);
    }

    private void newMethod(int i, int i2) {
        test(i);
        test(i2);
    }

    void none() {
        newMethod(0, 0);
    }

    void both() {
        newMethod(1, 1);
    }

    private void test(int i) {}
}
