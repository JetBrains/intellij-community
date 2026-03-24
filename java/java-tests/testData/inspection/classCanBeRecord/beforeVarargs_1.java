// "Convert to record class" "false"

class <caret>Test {
  private final int[] xyz;

  Test(int... data) {
    this.xyz = new int[10];
  }

  public int[] xyz() {
    return xyz;
  }
}
