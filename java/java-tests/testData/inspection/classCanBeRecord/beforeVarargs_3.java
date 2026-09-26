// "Convert to record class" "false"

class <caret>Test {
  private final int[] xyz;

  Test(int[] xyz, String... data) {
    this.xyz = xyz;
    System.out.println(data);
  }

  public int[] xyz() {
    return xyz;
  }
}
