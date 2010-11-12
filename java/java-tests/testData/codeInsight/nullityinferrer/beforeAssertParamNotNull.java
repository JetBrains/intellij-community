class Test {
  public String noNull(String text) {
    assert text != null;
    return "";
  }

  private void foo() {
    String str = "";
    assert str != null;
  }
}