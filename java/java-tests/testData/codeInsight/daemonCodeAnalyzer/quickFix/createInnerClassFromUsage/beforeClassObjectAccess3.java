// "Create Inner Class 'Foo'" "true"
public class Test {
  void foo(Class<? super Number> n){}
  void bar() {
    foo(Fo<caret>o.class);
  }
}