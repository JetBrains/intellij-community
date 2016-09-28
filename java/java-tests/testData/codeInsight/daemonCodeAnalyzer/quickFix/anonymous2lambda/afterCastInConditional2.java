// "Replace with lambda" "true"
class Test {
  {
    Runnable    r = b ? (IRunnable) () -> {
    } : null;
  }

  interface IRunnable extends Runnable {}
}