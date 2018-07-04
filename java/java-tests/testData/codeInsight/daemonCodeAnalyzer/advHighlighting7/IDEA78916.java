enum EnumPrivateMethodTest {
    FIRST {
        @Override
        public void execute() {
            this.<error descr="'firstMethod()' has private access in 'EnumPrivateMethodTest'">firstMethod</error>();
            String s = this.<error descr="'myDescription' has private access in 'EnumPrivateMethodTest'">myDescription</error>;
        }
    };

    public abstract void execute();

    private void firstMethod() {}
    private final String myDescription = "description";
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

class TestAnonymousContainer {
    private class Foo {
        private Foo() {}

        {
          new  Foo(){};
        }
    }

    class AB {
      private void foo(){}
    }
  
    {
      new AB() {}.<error descr="'foo()' has private access in 'TestAnonymousContainer.AB'">foo</error>();
    }
}