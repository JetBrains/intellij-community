// "Replace with lambda" "false"
class HelloLambda {
  final int x;

  HelloLambda() {
    Runnable r = new Runn<caret>able() {
      @Override
      public void run() {
        System.out.println(x);

      }
    };
    x = 1;

  }


}
