// "Remove 'new'" "true"

class A {
  class Nested {
    public static A factory() { return new A(); }
  }

}

class B {
  {
      /*
       * hello
       * world
       */
      A.// nested
              Nested./* method */
              factory// parameters
              /* below */
                      (/* parameters are empty */);
  }
}