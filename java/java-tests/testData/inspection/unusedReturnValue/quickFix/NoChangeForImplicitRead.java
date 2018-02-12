class Test {
  public String <caret>implicitRead() {
    return "";
  }

  {
    implicitRead();
  }
}