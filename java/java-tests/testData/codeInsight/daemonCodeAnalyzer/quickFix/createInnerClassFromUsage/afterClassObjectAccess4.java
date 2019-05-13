// "Create inner class 'Foo'" "true"
public class Test {
  void foo(Class<? extends Number> n){}
  void bar() {
    foo(Fo<caret>o);
  }

    private class Foo {
    }
}