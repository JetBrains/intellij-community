public class Foo {
  public Foo(String a) {
  }

  public Foo(String a, String b, String c) {
  }

  public static void main(String[] args) {
    Arrays.asList(new Foo("a", <caret>"b", "c", new Foo("d")
  }
}