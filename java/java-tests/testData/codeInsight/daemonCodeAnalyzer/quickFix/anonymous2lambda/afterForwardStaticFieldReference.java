// "Replace with lambda" "true-preview"
class HelloLambda {
  private final Runnable r = () -> System.out.println(x);
  private static int x = 0;
}