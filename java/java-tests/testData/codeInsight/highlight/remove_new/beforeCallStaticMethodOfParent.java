// "Remove 'new'" "true"

class A {
  public static void process() {}
}

class B extends A {
  {
    new /*
     * hello
     * world
     */ B.<caret>process();
  }
}