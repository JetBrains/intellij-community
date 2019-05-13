import java.util.function.Supplier;

class Foo {
  String s = "foo";

  Foo() {
    Supplier<String> fn = s::trim;
    s = "bar";
    System.out.println(fn.get());
  }

  public static void main(String[] args) {
    new Foo();
  }
}