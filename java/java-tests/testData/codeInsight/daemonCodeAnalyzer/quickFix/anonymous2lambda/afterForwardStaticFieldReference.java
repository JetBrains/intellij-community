// "Replace with lambda" "true"
class HelloLambda {
  private final Runnable r = () -> System.out.println(x);
  private static int x = 0;
}