class SideEffectReturn {
  private boolean isValidValue(String value) {
    try {
        Test.valueOf(value);
        return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  enum Test {
    A,
    B

  }
}