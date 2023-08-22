class NoThrows<caret> extends Parent implements Cloneable {
    private String[] ss;
}
class Parent implements Cloneable {

  public Parent clone() {
    try {
      return (Parent) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError()
    }
  }
}