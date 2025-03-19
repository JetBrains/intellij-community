class <caret>Data implements Cloneable {
  private String[] ss;

  public Data copy() {
    try {
      return (Data) clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }
}