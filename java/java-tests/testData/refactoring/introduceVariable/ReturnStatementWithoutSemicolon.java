class Test {
  public String bla() {
    return String.<caret>format("foo.bar.%s", "")
  }
}