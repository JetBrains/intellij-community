// "Create inner class 'Foo'" "true-preview"
public class Test {
  void foo(Class<? extends Number> n){}
  void bar() {
    foo(Fo<caret>o.class);
  }
}