// "Create Inner Class 'Foo'" "true"
public class Test {
  void foo(Class<Number> n){}
  void bar() {
    foo(Fo<caret>o.class);
  }
}