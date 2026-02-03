public class whilePolyadicWholeCondition {
  void test(int a, int b) {
    while (true) {
        boolean b1 = a < 0 && b > 0;
        if (!b1) break;
        a--;
      b++;
    }
  }
}