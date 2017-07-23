class Main {

  @interface Anno {
    Class value();
  }
  private static class A {}

  @Anno(A.class)
  class U {}
}