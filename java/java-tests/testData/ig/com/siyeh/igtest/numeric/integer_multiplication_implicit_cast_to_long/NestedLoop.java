public class NestedLoop {
  void test() {
    long value = 1;
    for (int a = 0; a < 2; a++) {
      for (int b = 0; b < 2; b++) {
        b++;
      }
      value = 2 * a;
    }
  }
}