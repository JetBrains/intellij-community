// "Create inner class 'Foo'" "true"
public class Test {
  void foo(Class<Number> n){}
  void bar() {
    foo(Fo<caret>o.class);
  }

    private class Foo extends Number {
    }
}