public class Test {
  class Super {
    Object foo() { return new Object(); }
  }
  class Sub extends Super{
    String foo() { return ""; }
  }
  public String get(final Super obj) {
    if (obj instanceof Sub) {
      return ((Sub)obj).foo();
    } else {
      return "The value is " + obj.foo();
    }
  }
}