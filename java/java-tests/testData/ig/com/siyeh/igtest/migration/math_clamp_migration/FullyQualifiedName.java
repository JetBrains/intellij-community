public final class FullyQualifiedName {
  void test(int val) {
    int res2 = <warning descr="Can be replaced with 'Math.clamp()'">java.lang.<caret>Math.min(java.lang.Math.max(val, 1), 100)</warning>;
  }

  static class Math {}
}
