interface Foo {
  
  sealed class A {}
  final class B extends A {}
  
  sealed class C {}
  non-sealed class D extends C {}

  sealed class E {}
  sealed class F extends E {}
  final class G extends F implements Foo {}
  
  sealed class H permits I {}
  final class I extends H {}
  
  sealed class J permits K {}
  non-sealed class K extends J {}
  
  final class L implements M {}
  sealed interface M permits L {}
  
  class N {}
  sealed interface O permits P {}
  final class P extends N implements O {}

  class R {}
  sealed interface S permits T {}
  final class T extends U implements S {}
  class U extends R {}
  
  class V {}
  sealed interface W permits X {}
  final class X implements W {}
  
  sealed class Recursive1 permits <error descr="Invalid permits clause: 'Recursive2' must directly extend 'Recursive1'">Recursive2</error> {}
  sealed class Recursive2 permits <error descr="Invalid permits clause: 'Recursive1' must directly extend 'Recursive2'">Recursive1</error> {}

  interface I1 {}
  
  sealed interface GrandParent permits Parent {}
  static sealed class Parent implements GrandParent permits Child {}
  static final class Child extends Parent {}
  static class RandomClass {} 
  
  
  static void testA(A a) {
    if (<error descr="Inconvertible types; cannot cast 'Foo.A' to 'Foo'">a instanceof Foo</error>)
      System.out.println("It's a Foo");
  }

  static void testC(C c) {
    if (c instanceof Foo)
      System.out.println("It's a Foo");
  }

  static void testE(E e) {
    if (e instanceof Foo)
      System.out.println("It's a Foo");
  }

  static void testH(H h) {
    if (<error descr="Inconvertible types; cannot cast 'Foo.H' to 'Foo'">h instanceof Foo</error>)
      System.out.println("It's a Foo");
  }

  static void testJ(J j) {
    if (j instanceof Foo)
      System.out.println("It's a Foo");
  }

  static void testL(L l) {
    if (l instanceof M)
      System.out.println("It's a M");
  }

  static void testN(N n) {
    if (n instanceof O)
      System.out.println("It's an O");
  }

  static void testR(R r) {
    if (r instanceof S)
      System.out.println("It's a S");
  }

  static void testV(V v) {
    if (<error descr="Inconvertible types; cannot cast 'Foo.V' to 'Foo.W'">v instanceof W</error>)
      System.out.println("It's a W");
  }

  static void testRecursive1(Recursive1 r1) {
    if (r1 instanceof I1) {}
  }
  
  static void testDeepSealedHierarchy(GrandParent gp) {
    if (<error descr="Inconvertible types; cannot cast 'Foo.GrandParent' to 'Foo.RandomClass'">gp instanceof RandomClass</error>) {
      System.out.println("It's a RandomClass");
    }
  }
}