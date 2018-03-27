class SideEffectReturn {
  private void testLoop(String value) {
      Test.valueOf(value);
  }

  enum Test {
    A,
    B

  }
}