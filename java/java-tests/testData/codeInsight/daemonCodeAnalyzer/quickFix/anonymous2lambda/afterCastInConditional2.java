// "Replace with lambda" "true-preview"
class Test {
  {
    Runnable    r = b ? (IRunnable) () -> {
    } : null;
  }

  interface IRunnable extends Runnable {}
}