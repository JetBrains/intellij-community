public class Test {

  {
    new Foo().method(new Ou<caret>);
  }

  static class Outer {
    static class Inner implements Intf {}
  }

  class Foo {
    public void method(Intf inner) {}
  }
  interface Intf {}
}