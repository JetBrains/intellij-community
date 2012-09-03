class Test {
  interface I {
    boolean foo(String s);
  }
  {
    ((I)s -> s.endsWith(".java")).getClass();
    I i = (I)s -> s.endsWith(".java");
    
  }
}
