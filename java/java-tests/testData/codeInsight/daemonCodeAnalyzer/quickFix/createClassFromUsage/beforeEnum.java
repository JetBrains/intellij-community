// "Create enum 'Foo'" "true-preview"
public class Test {
  void f(Class<? extends Enum> e) {}
  {
    f(Fo<caret>o.class);
  }
}