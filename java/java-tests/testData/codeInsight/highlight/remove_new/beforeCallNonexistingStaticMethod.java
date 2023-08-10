// "Remove 'new'" "false"

class A {
  public static A factory() { return new A(); }
  {
    new /*
     * hello
     * world
     */ A.<caret>create();
  }
}