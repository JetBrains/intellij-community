import javax.annotation.concurrent.GuardedBy;

class FieldAccessNotGuarded {

  @GuardedBy("this")
  private String one = "";

  @GuardedBy("FieldAccessNotGuarded.this")
  private String two = "";

  @GuardedBy("lock()")
  private String three = "";

  synchronized Object m() {
    one = "one";
    two = "two";
    return one;
  }

  void f() {
    synchronized (lock()) {
      three = "three";
    }
    synchronized (m()) {
      <warning descr="Access to field 'three' outside of declared guards">three</warning> = "four";
    }
  }

  private Object lock() {
    return new Object();
  }

  class Inner {
    synchronized void a() {
      <warning descr="Access to field 'one' outside of declared guards">one</warning> = "two";
    }

    void b() {
      synchronized(this) {
        <warning descr="Access to field 'one' outside of declared guards">one</warning> = "two";
      }

      synchronized(FieldAccessNotGuarded.this) {
        one = "one";
      }
    }
  }
}
class Example
{
  private final Distribution distribution = new Distribution();

  public void add(long value)
  {
    synchronized (distribution.lock) {
      distribution.total += value;
    }

  }

  protected static class Distribution
  {
    public final Object lock = new Object();

    @GuardedBy("lock")
    private long total = 0;
  }
}
class Example2
{
  private final Distribution distribution = new Distribution();

  public void add(long value)
  {
    synchronized (distribution.lock()) {
      distribution.total += value;
    }
  }

  protected static class Distribution
  {
    public Object lock() {
      return new Object();
    }

    @GuardedBy("lock()")
    private long total = 0;
  }
}
class Example3
{
  private final Distribution distribution = new Distribution();

  public void add(long value)
  {
    synchronized (Example3.Distribution.class) {
      distribution.total += value;
    }
  }

  protected static class Distribution
  {
    @GuardedBy("Distribution.class")
    private long total = 0;
  }
}

class Example4 {
  @GuardedBy("this")
  protected Object field;

  static class Example4Derived extends Example4 {
    synchronized void foo() {
      Object o = field;
    }
  }
}