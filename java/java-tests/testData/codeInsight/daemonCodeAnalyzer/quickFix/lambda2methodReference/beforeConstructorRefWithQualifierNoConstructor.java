// "Replace lambda with method reference" "false"
class OuterClass {
  class InnerClass { }

  public static void foo() {
    final OuterClass outerClassInstance = new OuterClass();

    doSomethingWithRunnable(() -> outerClassInstance.new Inner<caret>Class());
  }

  static void doSomethingWithRunnable(Runnable runnable) {
    runnable.run();
  }
}