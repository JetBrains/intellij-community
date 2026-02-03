class Test {
  static boolean isA() {
    return true;
  }

  static boolean isB() {
    return true;
  }

  private static void foo(final boolean explicit) {
    final boolean has = explicit ? isA() : isB();
    if (ha<caret>s) {
    }
  }
}