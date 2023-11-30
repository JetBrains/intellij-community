class Data<caret> implements Cloneable {
  private String[] ss;

  public Data copy() {
    try {
      return (Data) clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }

  void x() {
    new Object() {{
      try {
        Object clone = clone();
      } catch (CloneNotSupportedException e) {
        e.printStackTrace();
      }
    }};
  }
}