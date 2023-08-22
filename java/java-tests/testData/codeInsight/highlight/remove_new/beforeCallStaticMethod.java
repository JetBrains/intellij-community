// "Remove 'new'" "true"

class A {
  public static A factory() { return new A(); }
  {
    new /*
     * hello
     * world
     */ A. // call
        // to
        // a method
      <caret>factory/* with
      parameters
      */
      (/* empty
        */
        // param list
      );
  }
}