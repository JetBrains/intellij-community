class Demo {
  private Object field;

  @Override
  public boolean equals(Object obj) {
    if (this <caret>== obj) {
      return true;
    }
    else {
      Demo other = (Demo)obj;
      return field == other.field;
    }
  }
}