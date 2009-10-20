public class Foo {
    void foo() {
      Bar.Fubar b = true ? Bar.Fubar.Bar : <caret>
    }
}
  
public static class Bar {
  public enum Fubar {
    Foo, Bar
  }
}