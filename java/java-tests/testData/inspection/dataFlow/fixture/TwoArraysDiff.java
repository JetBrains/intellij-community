public class TwoArraysDiff {
  public void test(Iterable<String> net) {
    final int size = 3;
    final double[] d1 = new double[size];
    int count = 0;
    for (String s : net) {
      d1[count++] = calculate();
    }
    final double[] d2 = new double[size];
    count = 0;
    for (String s : net) {
      d2[count++] = calculate();
    }
    for (int i = 0; i < size; i++) {
      assertTrue(d2[i] < d1[i]);
    }
  }

  static void assertTrue(boolean b) {
    if (!b) {
      throw new AssertionError();
    }
  }

  native double calculate();
}
