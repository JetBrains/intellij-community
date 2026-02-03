public class Test {

  {
    new Foo<String>().method(new Outer.Inner<String>());<caret>
  }

  static class Outer {
    static class Inner<A> {}
  }

  class Foo<A> {
    public void method(Outer.Inner<A> inner) {}
  }
}