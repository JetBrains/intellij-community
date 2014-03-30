// "Replace with lambda" "true"
class HelloLambda {
  final int x;

  HelloLambda() {
    x = 1;
    Runnable r = new Runn<caret>able() {
      @Override
      public void run() {
        System.out.println(x);

      }
    };

  }


}
