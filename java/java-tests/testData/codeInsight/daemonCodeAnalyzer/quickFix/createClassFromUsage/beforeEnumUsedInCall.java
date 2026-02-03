// "Create enum 'Foo'" "true-preview"
public class Test {
  void f(I i) {}
  {
    f(Fo<caret>o.CONST);
  }
}

interface I {}