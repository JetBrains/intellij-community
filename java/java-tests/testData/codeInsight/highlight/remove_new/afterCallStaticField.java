// "Remove 'new'" "true"

class A {
  public static A field = new A();
  {
      /*
       * hello
       * world
       */
      A a = A. // call to
              // a field
              field;
  }
}