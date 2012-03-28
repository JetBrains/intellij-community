// "Pull method 'foo' to 'Foo' and make it abstract" "true"
public class Test {
  void bar() {
    abstract class Foo {
        abstract void foo();
    }
    class FooImpl extends Foo {
      @Override
      void foo(){}
    }
  }
}


