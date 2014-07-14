// "Replace with lambda" "true"
class HelloLambda {
  final int x;

  HelloLambda() {
    x = 1;
    Runnable r = () -> System.out.println(x);

  }


}
