class Test {
  public String <caret>provider() {
    return "";
  }

  {
    provider();
  }
}