// "Convert to local" "true"
class MyClassTest {

  private boolean editable = false;

    boolean isEditable() {
    return editable;
  }

  void setEditable(boolean editable) {
    this.editable = editable;
  }

  public void setEditable1(final boolean editable1) {
  }
}