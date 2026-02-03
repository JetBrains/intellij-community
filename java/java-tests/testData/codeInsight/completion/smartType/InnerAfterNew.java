public class Test {

  {
    new Foo<String>().method(new <caret>);
  }

  static class Outer {
    static class Inner<A> {}
  }

  class Foo<A> {
    public void method(Outer.Inner<A> inner) {}
  }
}