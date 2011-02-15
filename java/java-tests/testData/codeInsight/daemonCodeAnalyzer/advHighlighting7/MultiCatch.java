abstract class C {
  private static class NE { }
  private static class E extends Exception { }
  private static class E1 extends E { }
  private static class E2 extends E { }
  private static class E3 extends E { }
  private static class RE extends RuntimeException { }
  private interface I { }
  private static class IE1 extends E implements I { }
  private static class IE2 extends E implements I { }

  abstract void f() throws E1, E2;
  abstract void g() throws IE1, IE2;

  void m() {
    try { f(); } catch (E1 | E2 e) { }
    try { f(); } catch (E2 | E e) { e.printStackTrace(); }
    try { f(); } catch (E2 | E1 e) { } catch (E e) { } catch (RE e) { }
    try { f(); } catch (E1 | E e) { E ee = e; }
    try { g(); } catch (IE1 | IE2 e) { E ee = e; I ii = e; }

    try { f(); } catch (E1 | E2 | <error descr="Exception 'C.E3' is never thrown in the corresponding try block">E3</error> e) { }
    try { f(); } catch (<error descr="Exception 'C.E3' is never thrown in the corresponding try block">E3</error> | E e) { }

    try { f(); } catch (E | <error descr="Exception 'C.E1' has already been caught">E1</error> e) { }
    try { f(); } catch (E | <error descr="Exception 'C.E3' has already been caught">E3</error> e) { }
    try { f(); } catch (E e) { } catch (<error descr="Exception 'C.E1' has already been caught">E1</error> | <error descr="Exception 'C.E3' has already been caught">E3</error> e) { }
    try { f(); } catch (E1 | E e) { } catch (<error descr="Exception 'C.E2' has already been caught">E2</error> e) { }

    try { f(); } catch (E1 | E2 e) { } catch (E e) { <error descr="Incompatible types. Found: 'C.E', required: 'C.E1'">E1 ee = e;</error> }
    try { } catch (<error descr="Incompatible types. Found: 'C.RE | C.NE', required: 'java.lang.Throwable'">RE | <error descr="Exception 'C.NE' is never thrown in the corresponding try block">NE</error> e</error>) { }
    try { f(); } catch (E | RE e) { <error descr="Cannot assign a value to final variable 'e'">e = null</error>; }
  }
}