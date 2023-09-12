import java.util.List;

class CastToIncompatibleInterface {

  interface A {}
  interface B {
    default boolean acting() {
      return true;
    }
  }
  interface Z extends B {}
  class C {}

  boolean x(C c) {
    if (c instanceof Z) {
      Z z = ((Z)c);
    }
    if (c instanceof Z) {
      A a = ((<warning descr="Cast of expression with type 'C' to incompatible interface 'A'">A</warning>)c);
    }
    if (c instanceof A) {
      A a = ((A)c);
    }
    if (c instanceof Z) {
      B b = ((B)c);
    }
    if (c instanceof B) {
      B b = ((B)c);
    }
    return c instanceof B && ((B)c).acting();
  }

  void x(String s) {
    List l = <error descr="Inconvertible types; cannot cast 'java.lang.String' to 'java.util.List'">(List)s</error>;
  }
  
  void testUnderInstanceOf() {
    if (getC(0) instanceof B && ((B)getC(0)).acting()) {}
    if (getC(0) instanceof B && ((<warning descr="Cast of expression with type 'C' to incompatible interface 'B'">B</warning>)getC(1)).acting()) {}
  }

  interface Generic<T> {}
  class NonGeneric {}

  volatile NonGeneric ng;

  void testUnchecked() {
    if (ng instanceof Generic) {
      Generic<String> generic = (Generic<String>) ng;
    }
  }
  
  native C getC(int x);
}
class Foo { }
interface Bar { }
final class Main213 {

  static void x(Foo f, Bar b) {
    Bar b1 = (<warning descr="Cast of expression with type 'Foo' to incompatible interface 'Bar'">Bar</warning> )f;
    Foo f1 = (<warning descr="Cast of expression with type 'Bar' to incompatible class 'Foo'">Foo</warning>) b;
  }
}