// "Replace with lambda" "false"
class HelloLambda {
  private final Runnable r = new Run<caret>nable() {
    @Override
    public void run() {
      System.out.println(x);
    }
  };
  private int x = 0;
}