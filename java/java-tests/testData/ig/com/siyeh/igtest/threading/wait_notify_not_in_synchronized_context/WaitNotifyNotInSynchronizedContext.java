package com.siyeh.igtest.threading.wait_not_in_synchronized_context;

public class WaitNotifyNotInSynchronizedContext {
  private final Object lock = new Object();
  private final Object otherLock = new Object();

  void foo() throws InterruptedException {
    <warning descr="Call to 'otherLock.wait()' while not synchronized on 'otherLock'">otherLock.wait()</warning>;
    synchronized (this) {
      <warning descr="Call to 'lock.wait()' while not synchronized on 'lock'">lock.wait()</warning>;
    }
    synchronized (lock) {
      <warning descr="Call to 'wait()' while not synchronized on 'this'">wait()</warning>;
    }
  }

  synchronized void bar() throws InterruptedException {
    wait();
  }

  public static void main(String[] args) throws InterruptedException {
    new WaitNotifyNotInSynchronizedContext().foo();
  }

}
class More {
  private final Object lock = new Object();

  public  synchronized void bar() {
    try {
      <warning descr="Call to 'lock.wait()' while not synchronized on 'lock'">lock.wait()</warning>;
    } catch(InterruptedException e) {}
  }

  public  void barzoomb() throws InterruptedException {
    synchronized (lock) {
      lock.wait();
    }
  }
}
class NotifyNotInSynchronizedContext
{
  private final Object lock = new Object();

  public  void foo()
  {
    <warning descr="Call to 'lock.notify()' while not synchronized on 'lock'">lock.notify()</warning>;
  }
  public  synchronized void bar()
  {
    <warning descr="Call to 'lock.notify()' while not synchronized on 'lock'">lock.notify()</warning>;
  }

  public  void barzoomb() {
    synchronized (lock) {
      lock.notify();
    }
  }

  public  void fooAll()
  {
    <warning descr="Call to 'lock.notifyAll()' while not synchronized on 'lock'">lock.notifyAll()</warning>;
  }
  public  synchronized void barAll()
  {
    <warning descr="Call to 'lock.notifyAll()' while not synchronized on 'lock'">lock.notifyAll()</warning>;
  }

  public  void barzoombAll() {
    synchronized (lock) {
      lock.notifyAll();
    }
  }

  public void suppressed() throws InterruptedException {
    //noinspection NotifyNotInSynchronizedContext
    lock.notify();
    //noinspection WaitWhileNotSynced
    lock.wait();
  }

  /**
   * @GuardedBy(lock)
   */
  public void guarded() throws InterruptedException {
    lock.wait();
    lock.notifyAll();
  }
}
