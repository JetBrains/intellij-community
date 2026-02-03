class Test {

  void method(String... p) {
  }

  public void doSmth(String[] p) {
      method(p[2]);
      System.out.println(p[3].intValue());
  }
}