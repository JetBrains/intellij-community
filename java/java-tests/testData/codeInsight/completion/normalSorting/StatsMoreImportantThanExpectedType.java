public class AnotherTestClass {
  public static void main(String[] args) throws IOException {
    test(getnu<caret>);
  }

  private static void test(int i) {
  }

  public static NumberProvider getNumProvider() {
    return new NumberProvider();
  }

  public static int getNumber() {
    return 1;
  }

  private static class NumberProvider {
    public int getNumber() {
      return 1;
    }
  }
}