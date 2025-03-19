class Test {

  private int field = 5;

  private void reproduce() {
    if (field == 5)
      setF<caret>ield(10);
  }

  private void setField(int value) {
    field = value;
  }
}