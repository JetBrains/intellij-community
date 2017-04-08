interface A {
  void m();
}
interface A1 extends A {
  <error descr="Static method 'm()' in 'A1' cannot override instance method 'm()' in 'A'">private static void m()</error> {}
}
interface A2 extends A {
  <error descr="'m()' in 'A2' clashes with 'm()' in 'A'; attempting to assign weaker access privileges ('private'); was 'public'">private</error> void m() {}
}

interface B {
  private void m() {}
}
interface B1 extends B {
  private void m() {}
}
interface B2 extends B {
  private static void m() {}
}
interface B3 extends B {
  default void m() {}
}
interface B4 extends B {
  void m();
}
