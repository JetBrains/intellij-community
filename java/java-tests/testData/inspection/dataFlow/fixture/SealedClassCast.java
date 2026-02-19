interface Foo {

  sealed class A permits B, C {}
  final class B extends A {}
  sealed class C extends A permits D {}
  final class D extends C {}

  static void test(A a) {
    if (<error descr="Inconvertible types; cannot cast 'Foo.A' to 'Foo'"><warning descr="Condition 'a instanceof Foo' is always 'false'">a instanceof Foo</warning></error>)
    System.out.println("This is a Foo");
  }

}