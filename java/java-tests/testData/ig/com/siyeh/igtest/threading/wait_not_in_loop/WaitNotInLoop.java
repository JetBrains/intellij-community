class WaitNotInLoop {

  private final Object lock = new Object();

  public void f() throws InterruptedException {
    lock.<warning descr="Call to 'wait()' is not in loop">wait</warning>();
  }
}