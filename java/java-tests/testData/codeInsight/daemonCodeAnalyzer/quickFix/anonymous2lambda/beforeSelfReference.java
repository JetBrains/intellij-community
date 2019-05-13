// "Replace with lambda" "false"
class Test {
  Runnable runnable = new Runn<caret>able() {
    @Override
    public void run() {
      System.out.println(runnable);
    }
  };
}