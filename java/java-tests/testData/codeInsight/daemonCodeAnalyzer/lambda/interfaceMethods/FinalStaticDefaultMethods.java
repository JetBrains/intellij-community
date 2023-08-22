interface A {
  <error descr="Modifier 'final' not allowed here">final</error> static void foo() {}
  <error descr="Modifier 'final' not allowed here">final</error> default void foo() {}
}
