class SideEffectReturn {
  private boolean isValidValue(String value) {
    if (!value.isEmpty())
      return <warning descr="Condition 'Test.valueOf(value) != null' is always 'true'">Test.valueOf<caret>(value) != null</warning>;
    return false;
  }

  enum Test {
    A,
    B

  }
}