// "Convert to record class" "true-preview"

class <caret>Foo {
  /**
   * head doc for x
   * @since 1.1
   */
  // another head doc for x
  public final int x; //tail doc for x

  // head doc for y
  public final /*
        internal doc for y
    */ int y; //tail doc for y

  public final int z;

  public Foo(int x, int y, int z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }
}
