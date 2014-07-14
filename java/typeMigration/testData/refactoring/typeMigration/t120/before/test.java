class Test {

  void method(Integer p) {
  }

  public void doSmth(Integer[] p) {
      method(p[2]);
      System.out.println(p[3].intValue());
  }
}