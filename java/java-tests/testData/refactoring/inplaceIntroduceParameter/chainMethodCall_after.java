class Test {

  private void test(boolean val) {
    TestSubject subject = new TestSubject();
    boolean flag = false;

    if (val != flag) {
      System.out.println(val);
    }
  }

  public static class TestSubject {
    public TestSubject2 getSubject2() {
      return new TestSubject2();
    }
  }

  public static class TestSubject2 {
    public boolean getVal() {
      return true;
    }
  }
}
