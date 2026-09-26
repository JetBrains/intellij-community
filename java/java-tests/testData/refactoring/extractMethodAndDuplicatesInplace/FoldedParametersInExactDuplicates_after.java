public class Test {

  void test(StringBuilder[] sbs, int i, int j, String s) {
    int a = getLength(sbs[i]);
    int b = getLength(sbs[j]);
  }

    private static int getLength(StringBuilder sbs) {
        return sbs.length();
    }
}