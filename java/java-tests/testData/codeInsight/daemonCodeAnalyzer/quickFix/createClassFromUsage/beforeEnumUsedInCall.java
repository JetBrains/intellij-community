// "Create enum 'Foo'" "true"
public class Test {
  void f(I i) {}
  {
    f(Fo<caret>o.CONST);
  }
}

interface I {}