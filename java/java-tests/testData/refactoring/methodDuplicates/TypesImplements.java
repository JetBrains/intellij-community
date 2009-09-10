class Types {
  public char <caret>method(CharSequence v) {
    return v.charAt(0);
  }

  public void context() {
    String v = "child type";
    char c = v.charAt(0);
  }
}
