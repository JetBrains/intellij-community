// "Replace with lambda" "false"
class Test {
  {
    Runnable x = new Runn<caret>able() {
      public void run() {
        this.toString();
      }
    };

  }
}