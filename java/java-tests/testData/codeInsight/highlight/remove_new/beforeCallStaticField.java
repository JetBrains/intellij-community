// "Remove 'new'" "true"

class A {
  public static A field = new A();
  {
    A a = new /*
     * hello
     * world
     */ A. // call to
           // a field
            <caret>field;
  }
}