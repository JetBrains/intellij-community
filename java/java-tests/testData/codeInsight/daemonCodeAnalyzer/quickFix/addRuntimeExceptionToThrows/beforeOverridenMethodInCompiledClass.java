// "Add 'throws UnsupportedOperationException' to method signature" "true-preview"
class A {
  @Override
  public String toString() {
    return super.toString();
  }

  static class B extends A {
    @Override
    public String toString() {
      throw new Unsupport<caret>edOperationException();
    }
  }
}