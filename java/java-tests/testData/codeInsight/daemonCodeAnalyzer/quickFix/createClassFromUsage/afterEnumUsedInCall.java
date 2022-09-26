// "Create enum 'Foo'" "true-preview"
public class Test {
  void f(I i) {}
  {
    f(Foo.CONST);
  }
}

interface I {}

public enum Foo implements I {}