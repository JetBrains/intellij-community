public class Test {
    public Test(Test test) {
    }

    private class Inner {
        public void x() {
            new MyClass();
        }

        private static class MyClass extends Test {
            public MyClass() {
                super(Test.this);
            }
        }

    }
}
