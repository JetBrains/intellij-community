import javax.annotation.concurrent.GuardedBy;

class CheapReadWriteLock {
  // Employs the cheap read-write lock trick
  // All mutative operations MUST be done with the 'this' lock held
  @GuardedBy("this") private volatile int value;

  public int getValue() { return value; }

  public synchronized int increment() {
    return value++;
  }
}