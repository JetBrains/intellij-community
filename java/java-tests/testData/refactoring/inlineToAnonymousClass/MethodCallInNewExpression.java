public class A {
    private class <caret>Inner {
        void doTest() {
        }
    }

    public void test() {
        new Inner().doTest();
    }
}