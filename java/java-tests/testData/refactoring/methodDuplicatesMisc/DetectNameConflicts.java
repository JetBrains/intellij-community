class Test {
  public Foo createFoo() {
        return new Foo() {
            public void bar() {
                System.out.println(1);
            }
        };
    }

    public void b<caret>ar() {
        System.out.println(1);
    }

    public interface Foo {
        void bar();
    }
}