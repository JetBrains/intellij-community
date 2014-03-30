enum EnumPrivateMethodTest {
    FIRST {
        @Override
        public void execute() {
            this.<error descr="'firstMethod()' has private access in 'EnumPrivateMethodTest'">firstMethod</error>();
        }
    };

    public abstract void execute();

    private void firstMethod() {}
}

abstract class EnumPrivateMethodTest1 {
    EnumPrivateMethodTest1 FIRST = new EnumPrivateMethodTest1() {
        @Override
        public void execute() {
            this.<error descr="'firstMethod()' has private access in 'EnumPrivateMethodTest1'">firstMethod</error>();
        }
    };

    public abstract void execute();

    private void firstMethod() {}
}

abstract class EnumPrivateMethodTest2 {
    EnumPrivateMethodTest2 FIRST = new EnumPrivateMethodTest2() {
        @Override
        public void execute() {
            firstMethod();
        }
    };

    public abstract void execute();

    private void firstMethod() {}
}

class Test {
    private class Foo {
        private Foo() {}

        {
          new  Foo(){};
        }
    }
}