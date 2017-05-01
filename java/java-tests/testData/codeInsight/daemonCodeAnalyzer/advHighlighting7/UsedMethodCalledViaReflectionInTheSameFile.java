class MyTest {
  public static void main(String[] args) throws Throwable {
    MyTest.class.getMethod("abcdef");
  }

  private void abcdef() {}
}