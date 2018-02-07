class IFoo {
  /**
   * @throws I<caret>
   */
  void foo() {
    throw new IllegalArgumentException();
  }
}
