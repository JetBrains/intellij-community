// "Replace with lambda" "true"
class HelloLambda {
  public HelloLambda() {
    Runnable r = () -> System.out.println(x);
  }
  private final int x = 0;
}