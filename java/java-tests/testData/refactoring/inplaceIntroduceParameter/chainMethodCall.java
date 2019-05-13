class Test {

  private void test() {
    TestSubject subject = new TestSubject();
    boolean flag = false;

    if (subject.getSubject2().get<caret>Val() != flag) {
      System.out.println(subject.getSubject2().getVal());
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
