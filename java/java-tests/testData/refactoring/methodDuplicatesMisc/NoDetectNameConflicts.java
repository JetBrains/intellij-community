class Test {
  public Foo createFoo() {
        return new Foo() {
            public void bar() {
                System.out.println(1);
            }
        };
    }

    public void b<caret>ar(int i) {
        System.out.println(i);
    }

    public interface Foo {
        void bar();
    }
}