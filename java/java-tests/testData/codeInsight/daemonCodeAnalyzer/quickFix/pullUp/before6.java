// "Pull method 'foo' to 'Foo' and make it abstract" "true"
public class Test {
  void bar() {
    class Foo {}
    class FooImpl extends Foo {
      @Overr<caret>ide
      void foo(){}
    }
  }
}


