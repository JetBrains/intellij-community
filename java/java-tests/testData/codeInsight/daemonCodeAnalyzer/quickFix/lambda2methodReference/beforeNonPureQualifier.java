// "Replace lambda with method reference" "false"
class Example {
  static Runnable runner;

  public static void main(String[] args) {
    lambda = () -> runn<caret>er.run();
  }
}