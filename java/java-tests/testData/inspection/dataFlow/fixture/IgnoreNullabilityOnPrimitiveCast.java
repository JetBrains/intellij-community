import foo.*;

class IgnoreNullabilityOnPrimitiveCast {
  private static int map(@Nullable int[] mapping, int idx) {
    return mapping[idx];
  }

  static final int TEST;

  static {
    TEST = 4;
  }

  final int[] arr = {TEST};
  final int val = TEST;
}