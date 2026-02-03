class Foo {
  private volatile X volatileX;

  public void outer() {
    <caret>inner(volatileX);
  }

  private void inner(X localX) {
    assert localX == localX;
  }
} 
