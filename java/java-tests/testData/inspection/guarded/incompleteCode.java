import javax.annotation.concurrent.GuardedBy;

class CheapReadWriteLock {
  @GuardedBy(<error descr="Incompatible types. Found: 'int', required: 'java.lang.String'">1</error>) private volatile int value;

  public int getValue() { return value; }

  public synchronized int increment() {
    return value++;
  }
}