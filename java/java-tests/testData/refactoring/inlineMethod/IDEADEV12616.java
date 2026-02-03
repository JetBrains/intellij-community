class Test {
  public void foo() {
      String s = <caret>bar();
  }

  private String bar() {
    String result = null;
    assert result != null;
    return result;
  }
}