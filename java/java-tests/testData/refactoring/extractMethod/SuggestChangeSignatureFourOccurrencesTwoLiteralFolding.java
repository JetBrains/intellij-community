public class C {
    void second() {
        <selection> test(0);
        test(1);</selection>
    }

    void none() {
        test(0);
        test(0);
    }

    void both() {
        test(1);
        test(1);
    }

    void first() {
        test(1);
        test(0);
    }

    private void test(int i) {}
}
