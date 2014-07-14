class Test {
  Test myParent;

  public Test(Test parent) {
    myParent = parent;
  }

  class TestImpl extends Test {
    public TestImpl(Test parent) {
      super(parent);
    }
  }
}