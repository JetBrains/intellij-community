// "Remove 'new'" "true"

class A {
  public static void process() {}
}

class B extends A {
  {
      /*
       * hello
       * world
       */
      B.process();
  }
}