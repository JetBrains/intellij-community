class Test {

  void m(Integer... p) {}

  public void doSmth() {
    final Integer[] array = {(1), 2};
    m(array);
    m(3, 4, 5 + 6);
  }
}