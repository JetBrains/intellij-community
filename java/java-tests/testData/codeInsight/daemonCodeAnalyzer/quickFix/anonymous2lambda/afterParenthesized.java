// "Replace with lambda" "true"
class Test {
  private void doSomething() {
    ((Runnable) () -> System.out.println()).run();
  }
}