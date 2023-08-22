class XXX {
  int value = 10;

  void foo() {
    int value = <caret>getValue();
  }

  public int getValue() {
    return value;
  }
}
