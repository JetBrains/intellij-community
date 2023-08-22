// "Convert field to local variable in method 'setEditable1'" "true-preview"
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