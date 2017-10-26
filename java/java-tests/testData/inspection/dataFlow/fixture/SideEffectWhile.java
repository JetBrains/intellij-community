class SideEffectReturn {
  private void testLoop(String value) {
    while(<warning descr="Condition 'Test.valueOf(value) == null' is always 'false'">Test.v<caret>alueOf(value) == null</warning>) {

    }
  }

  enum Test {
    A,
    B

  }
}