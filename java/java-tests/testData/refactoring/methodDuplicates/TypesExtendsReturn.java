class Types {
  public String <caret>method(Object v) {
    return v.toString();
  }

  public void context() {
    String v = "child type";
    Object o = v.toString();
  }
}
