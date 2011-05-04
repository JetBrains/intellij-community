abstract class C {
  private static class NE { }
  private static class E extends Exception { public String s; }
  private static class E1 extends E { }
  private static class E2 extends E { }
  private static class E3 extends E { }
  private static class E4 extends E { }
  private static class RE extends RuntimeException { }
  private interface I<T> { }
  private static class IE1 extends E implements I<Integer> { }
  private static class IE2 extends E implements I<Long> { }
  private static class F<X> { F(X x) { } }

  abstract void f() throws E1, E2;
  abstract void g() throws IE1, IE2;

  <T extends Throwable> void m() {
    try { f(); } catch (E1 | E2 e) { }
    try { f(); } catch (E2 | E1 e) { e.printStackTrace(); System.out.println(e.s); }
    try { f(); } catch (E2 | E1 e) { } catch (E e) { } catch (RE e) { }
    try { f(); } catch (E1 | E2 e) { E ee = e; }
    try { g(); } catch (IE1 | IE2 e) { E ee = e; I ii = e; }
    try { g(); } catch (IE1 | IE2 e) { F<?> f = new F<>(e); }
    try { g(); } catch (IE1 | IE2 e) { new F<I<? extends Number>>(e); }

    try { } catch (<error descr="Incompatible types. Found: 'C.RE | C.NE', required: 'java.lang.Throwable'">RE | NE e</error>) { }
    try { } catch (<error descr="Incompatible types. Found: 'C.RE | C.NE[]', required: 'java.lang.Throwable'">RE | NE e[]</error>) { }
    try { f(); } catch (<error descr="Incompatible types. Found: 'C.E | T[]', required: 'java.lang.Throwable'">E | T e[]</error>) { } catch(E e) { }
    try { f(); } catch (E | <error descr="Cannot catch type parameters">T</error> e) { }

    try { f(); } catch (<error descr="Types in multi-catch must be disjoint: 'C.E1' is a subclass of 'C.E'">E1</error> | E ignore) { }
    try { f(); } catch (E | <error descr="Types in multi-catch must be disjoint: 'C.E1' is a subclass of 'C.E'">E1</error> ignore) { }
    try { f(); } catch (<error descr="Types in multi-catch must be disjoint: 'C.E' is a subclass of 'C.E'">E</error> | E ignore) { }

    try { f(); } catch (E1 | E2 | <error descr="Exception 'C.E3' is never thrown in the corresponding try block">E3</error> e) { }
    try { f(); } catch (<error descr="Exception 'C.E3' is never thrown in the corresponding try block">E3</error> | <error descr="Exception 'C.E4' is never thrown in the corresponding try block">E4</error> | RE e) { }

    try { f(); } catch (E e) { } catch (<error descr="Exception 'C.E1' has already been caught">E1</error> | <error descr="Exception 'C.E3' has already been caught">E3</error> e) { }
    try { f(); } catch (E1 | E2 e) { } catch (<error descr="Exception 'C.E2' has already been caught">E2</error> e) { }

    try { f(); } catch (E1 | E2 e) { } catch (E e) { <error descr="Incompatible types. Found: 'C.E', required: 'C.E1'">E1 ee = e;</error> }
    try { f(); } catch (E | RE e) { <error descr="Cannot assign a value to final variable 'e'">e = null</error>; }
  }
}