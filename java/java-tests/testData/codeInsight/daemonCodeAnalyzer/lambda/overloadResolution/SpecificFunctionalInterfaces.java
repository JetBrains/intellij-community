class Main {

  void perform(Runnable r) {
    System.out.println(r);
  }

  <T extends Throwable> void perform(TRunnable<T> r) {
    System.out.println(r);
  }


  interface TRunnable<T extends Throwable> {
    void run() throws T;
  }

  {
    perform<error descr="Ambiguous method call: both 'Main.perform(Runnable)' and 'Main.perform(TRunnable<Throwable>)' match">(() -> {})</error>;
  }
}