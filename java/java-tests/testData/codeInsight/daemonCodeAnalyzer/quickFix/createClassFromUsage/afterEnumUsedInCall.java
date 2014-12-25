// "Create enum 'Foo'" "true"
public class Test {
  void f(I i) {}
  {
    f(Foo.CONST);
  }
}

interface I {}

public enum Foo implements I {}