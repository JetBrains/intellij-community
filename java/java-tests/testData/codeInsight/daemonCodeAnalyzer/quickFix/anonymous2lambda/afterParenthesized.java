// "Replace with lambda" "true-preview"
class Test {
  private void doSomething() {
    ((Runnable) () -> System.out.println()).run();
  }
}