import javax.annotation.concurrent.*;

class UnknownGuard {
  @GuardedBy("itself")
  private final Object one = new Object();

  @GuardedBy(<warning descr="Unknown @GuardedBy reference \"nothing\"">"nothing"</warning>)
  private Object two = new Object();

  @GuardedBy("this")
  private Object three = new Object();

  @GuardedBy("UnknownGuard.this")
  private Object four = new Object();

  @GuardedBy(<warning descr="Unknown @GuardedBy reference \"Nothing.this\"">"Nothing.this"</warning>)
  private Object five = new Object();

  @GuardedBy("lock()")
  private Object six = new Object();
  private Object lock() {
    return new Object();
  }

  @GuardedBy(<warning descr="Unknown @GuardedBy reference \"wrong()\"">"wrong()"</warning>)
  private Object seven = new Object();
  private void wrong() {}

  @GuardedBy(<warning descr="Unknown @GuardedBy reference \"wrong2()\"">"wrong2()"</warning>)
  private Object eight = new Object();
  private void wrong2(int i) {}

  @GuardedBy(<warning descr="Unknown @GuardedBy reference \"nothing()\"">"nothing()"</warning>)
  private Object nine = new Object();

  @GuardedBy("one")
  private Object ten = new Object();

  @GuardedBy("this.one")
  private Object eleven = new Object();

  @GuardedBy("UnknownGuard.this.one")
  private Object twelve = new Object();

  @GuardedBy(<warning descr="Unknown @GuardedBy reference \"wrong\"">"wrong"</warning>)
  private Object thirteen = new Object();
  private int wrong = 1;

  @GuardedBy("java.lang.String.class")
  private Object fourteen = new Object();

  @GuardedBy("UnknownGuard.class")
  private Object fifteen = new Object();

  @GuardedBy(<warning descr="Unknown @GuardedBy reference \"Nothing.class\"">"Nothing.class"</warning>)
  private Object sixteen = new Object();

  @GuardedBy(<warning descr="Unknown @GuardedBy reference \"Wrong.this\"">"Wrong.this"</warning>)
  private Object seventeen = new Object();
  class Wrong {}

  /**
   * <warning descr="Unknown @GuardedBy reference \"wrong\"">@GuardedBy(wrong)</warning>
   */
  private Object eighteen = new Object();

  /**
   * @GuardedBy(this)
   */
  private Object nineteen = new Object();

  @GuardedBy("Inner.LOCK")
  private Object twenty = new Object();
  static class Inner {
    private static final Object LOCK = new Object();
  }

  @GuardedBy(<warning descr="Unknown @GuardedBy reference \"Inner.nothing\"">"Inner.nothing"</warning>)
  private Object twentyone = new Object();

  @GuardedBy(<warning descr="Unknown @GuardedBy reference \"Nothing.LOCK\"">"Nothing.LOCK"</warning>)
  private Object twentytwo = new Object();
}