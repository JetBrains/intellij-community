class WithCondition {
  void testForEach() {
    return;
    <error descr="Unreachable statement">for</error> (int i: this.array()) {
      System.out.println("Never");
    }
  }

  int[] array() { return new int[0]; }
}