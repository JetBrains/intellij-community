class Data implements Cloneable {
  private String[] ss;

  public Data copy() {
    try {
      return (Data) super.clone();
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

    @Override
    protected Data clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
}