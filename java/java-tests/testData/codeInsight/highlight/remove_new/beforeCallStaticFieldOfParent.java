// "Remove 'new'" "true"

class A {
  public static A field = new A();
}

class B extends A {
  {
    A a = new /*
     * hello
     * world
     */ B.<caret>field;
  }
}