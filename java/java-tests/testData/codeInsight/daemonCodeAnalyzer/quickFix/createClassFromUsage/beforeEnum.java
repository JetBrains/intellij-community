// "Create enum 'Foo'" "true"
public class Test {
  void f(Class<? extends Enum> e) {}
  {
    f(Fo<caret>o.class);
  }
}