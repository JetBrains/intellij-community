public class Foo {
  private Foo(String s, String s2) {
    this(s + " " + s2);
  }
  private Foo(String s) {}
  
  {
    Foo foo = new F<caret>oo("a", "b")
  }
}