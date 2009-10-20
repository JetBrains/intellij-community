public class Foo {
    void foo() {
      Bar.Fubar b = true ? <caret>
    }
}
  
public static class Bar {
  public enum Fubar {
    Foo, Bar
  }
}