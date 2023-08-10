// "Remove 'new'" "true"

class A {
  public static A factory() { return new A(); }
  {
      /*
       * hello
       * world
       */
      A. // call
              // to
              // a method
                      factory/* with
      parameters
      */
              (/* empty
                       */
                      // param list
              );
  }
}