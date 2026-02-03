// "Remove 'new'" "true"

class A {
  public static A field = new A();
}

class B extends A {
  {
      /*
       * hello
       * world
       */
      A a = B.field;
  }
}