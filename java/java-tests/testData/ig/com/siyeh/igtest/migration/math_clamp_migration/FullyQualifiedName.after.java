public final class FullyQualifiedName {
  void test(int val) {
    int res2 = java.lang.Math.clamp(val, 1, 100);
  }

  static class Math {}
}
