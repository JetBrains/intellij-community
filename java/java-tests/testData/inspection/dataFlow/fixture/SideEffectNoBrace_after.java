class SideEffectReturn {
  private boolean isValidValue(String value) {
    if (!value.isEmpty()) {
        Test.valueOf(value);
        return true;
    }
    return false;
  }

  enum Test {
    A,
    B

  }
}