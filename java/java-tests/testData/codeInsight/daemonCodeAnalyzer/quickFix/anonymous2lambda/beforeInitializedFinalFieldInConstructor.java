// "Replace with lambda" "true"
class HelloLambda {
  public HelloLambda() {
    Runnable r = new Run<caret>nable() {
      @Override
      public void run () {
        System.out.println(x);
      }
    } ;
  }
  private final int x = 0;
}