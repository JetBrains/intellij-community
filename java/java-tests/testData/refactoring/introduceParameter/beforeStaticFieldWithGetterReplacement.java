public class R {
  private static int ourInt;

  public static int getOurInt() {
    return ourInt;
  }

  public static void doSmth() {
    System.out.println(<selection>ourInt</selection>);
  }
}

class Usage {
  void foo() {
    R.doSmth();
  }
}