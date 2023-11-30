public class Foo {
  public Foo(String a) {
  }

  public Foo(String a, String b) {
  }

  public static void println(Object o){}
  public static void main(String[] args) {
      println(new Foo("a", "b"));
  }
}