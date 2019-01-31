public class Test {

  public static void main(String[] args) {
    fooBarPublic(239);
    fooBarPublic(239);
    fooBarPublic(239);

    fooBarPrivate(100);
    fooBarPrivate(100);
    fooBarPrivate(100);
  }

  public static int fooBarPublic(int val) {
    return 239;
  }

  private static int fooBarPrivate(int val) {
    return 239;
  }
}
