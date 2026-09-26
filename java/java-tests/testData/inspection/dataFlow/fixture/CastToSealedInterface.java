class Foo {
  static void test(Foo foo) {
   if (<error descr="Inconvertible types; cannot cast 'Foo' to 'I'"><warning descr="Condition 'foo instanceof I' is always 'false'">foo instanceof I</warning></error>)
    System.out.println("This is a Foo");
   }
}

sealed interface I {}
final class C implements I {} 