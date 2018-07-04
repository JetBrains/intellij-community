class SideEffectReturn {
  private boolean isValidValue(String value) {
    try {
      return <warning descr="Condition 'Test.valueOf(value/*oops*/) != null' is always 'true'">Test.valueOf(value/*oops*/<caret>) != null</warning>;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  enum Test {
    A,
    B

  }
}