// "Convert to record class" "false"

// This test makes sure we don't change semantics (constructor overload resolution).
class Usage {
  void use() {
    new Test(1);
  }
}

class <caret>Test {
  private final int x;

  Test(int... xs) {
    this.x = xs[0] + 1;
  }

  public int x() {
    return x;
  }
}
