// "Remove 'new'" "true"

class A {
  class Nested {
    public static A factory() { return new A(); }
  }

}

class B {
  {
    new /*
     * hello
     * world
     */ A.// nested
      Nested./* method */
    <caret>factory// parameters
      /* below */
        (/* parameters are empty */);
  }
}