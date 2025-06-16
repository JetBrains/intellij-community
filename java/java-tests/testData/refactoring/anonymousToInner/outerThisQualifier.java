public class Test {
    public Test(Test test) {
    }

    private class Inner {
        public void x() {
            new <caret>Test(Test.this) {

            };
        }
    }
}
