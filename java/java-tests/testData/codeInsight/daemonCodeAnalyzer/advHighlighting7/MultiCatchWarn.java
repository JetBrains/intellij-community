abstract class C {
  private static class E extends Exception { }
  private static class E1 extends E { }
  private static class E2 extends E { }

  abstract void f() throws E1, E2;

  void m() {
    try { f(); } catch (E1 | E2 ignore) { }
    try { f(); } catch (<warning descr="Exception 'C.E1' is also caught by 'C.E'">E1</warning> | E ignore) { }
  }
}