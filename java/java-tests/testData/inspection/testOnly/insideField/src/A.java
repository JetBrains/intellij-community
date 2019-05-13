public class A {
  @org.jetbrains.annotations.TestOnly
  public static final String MY_FIELD = "VALUE";

  @org.jetbrains.annotations.TestOnly
  public static final Runnable MY_TEST_ONLY_RUNNABLE = new Runnable() {
    public void run() {
      System.out.println(MY_FIELD);
    }
  };

  public static final Runnable MY_PRODUCTION_RUNNABLE = new Runnable() {
    public void run() {
      System.out.println(MY_FIELD);
    }
  };
}