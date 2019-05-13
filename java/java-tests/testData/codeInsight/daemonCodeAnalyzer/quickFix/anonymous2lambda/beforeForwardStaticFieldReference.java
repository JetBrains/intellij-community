// "Replace with lambda" "true"
class HelloLambda {
  private final Runnable r = new Run<caret>nable() {
    @Override
    public void run() {
      System.out.println(x);
    }
  };
  private static int x = 0;
}