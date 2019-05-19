class Test {
  interface I {
    boolean foo(String s);
  }
  {
    ((I)s -> s.endsWith(".java")).getClass();
    I i = (<warning descr="Casting 's -> {...}' to 'I' is redundant">I</warning>)s -> s.endsWith(".java");

  }
}
