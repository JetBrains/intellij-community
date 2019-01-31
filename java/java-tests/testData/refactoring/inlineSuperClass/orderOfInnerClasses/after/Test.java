class Test {
  public Test() {
  }

    private static class Inner1 {
      Inner2 dummy = Inner2.DUMMY;
    }

    private static class Inner2 {

      public static final Inner2 DUMMY = new Inner2();
    }
}