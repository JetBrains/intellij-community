class XXX {
  int value = 10;

  void x(boolean b) {
    if (b) <caret>getValue();
  }
  public int getValue() {
    return value;
  }
}