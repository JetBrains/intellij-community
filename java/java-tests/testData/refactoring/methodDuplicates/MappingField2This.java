class Mapping {
  private int myInt;
  private Mapping myField;

  public void <caret>method() {
    myInt = hashCode();
  }

  public void context() {
    myInt = myField.hashCode();
  }
}
