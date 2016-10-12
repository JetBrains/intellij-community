interface Test {
  /**
   * foo's javadoc
   */
  void foo();
}

interface B extends Test {
    @Override
    void foo();
}