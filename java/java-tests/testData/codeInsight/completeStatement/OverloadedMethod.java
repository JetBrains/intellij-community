public class Foo {
  public Foo(String a) {
  }

  public Foo(String a, String b) {
  }

  public static void main(String[] args) {
    System.out.println(new Foo("a", "b")<caret>;
  }
}