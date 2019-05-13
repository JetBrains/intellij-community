class Test {
  TestImpl myParent;

  public Test(TestImpl parent) {
    myParent = parent;
  }

  class TestImpl extends Test {
    public TestImpl(TestImpl parent) {
      super(parent);
    }
  }
}