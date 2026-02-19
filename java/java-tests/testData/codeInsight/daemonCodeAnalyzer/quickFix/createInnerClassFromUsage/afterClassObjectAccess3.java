// "Create inner class 'Foo'" "true-preview"
public class Test {
  void foo(Class<? super Number> n){}
  void bar() {
    foo(Fo<caret>o.class);
  }

    private class Foo {
    }
}