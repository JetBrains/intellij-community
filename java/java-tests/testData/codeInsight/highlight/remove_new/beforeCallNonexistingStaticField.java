// "Remove 'new'" "false"

class A {
  public static A field = new A();
  {
    A a = new /*
     * hello
     * world
     */ A.<caret>value;
  }
}