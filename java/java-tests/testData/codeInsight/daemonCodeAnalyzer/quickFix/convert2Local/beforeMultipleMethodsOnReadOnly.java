// "Convert to local" "false"
class MyClassTest {

  private boolean edit<caret>able = false;
  private boolean editable1 = false;

  boolean isEditable() {
    return editable;
  }

  void setEditable(boolean editable) {
    this.editable = editable;
  }

  public void setEditable1(final boolean editable1) {
    this.editable1 = editable1;
  }
}