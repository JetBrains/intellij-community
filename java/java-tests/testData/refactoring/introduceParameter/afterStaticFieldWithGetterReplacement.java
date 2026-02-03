public class R {
  private static int ourInt;

  public static int getOurInt() {
    return ourInt;
  }

  public static void doSmth(final int anObject) {
    System.out.println(anObject);
  }
}

class Usage {
  void foo() {
    R.doSmth(R.getOurInt());
  }
}