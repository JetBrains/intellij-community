// "Replace with lambda" "true-preview"
class Test {
  private void doSomething() {
    (new <caret>Runnable() {
      public void run() {
        System.out.println();
      }
    }).run();
  }
}