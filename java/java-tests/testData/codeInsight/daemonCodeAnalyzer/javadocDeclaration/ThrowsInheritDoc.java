interface Base {
  /**
   * @throws java.lang.RuntimeException if something wrong happens
   */
  void test();
}

class Test implements Base {
  /**
   * @throws java.lang.RuntimeException {@inheritDoc}
   * <warning descr="'@throws' tag description is missing">@throws</warning> java.lang.Error
   */
  public void test() {
    
  }
}