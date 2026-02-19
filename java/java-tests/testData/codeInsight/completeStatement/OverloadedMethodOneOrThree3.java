public class Foo {
  public Foo(String a) {
  }

  public Foo(String a, String b, String c) {
  }

  public static void main(String[] args) {
    Arrays.asList(new Foo(<caret>"a", new Foo("b"), new Foo("c"), new Foo("d")
  }
}