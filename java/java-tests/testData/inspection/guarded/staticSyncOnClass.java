import javax.annotation.concurrent.GuardedBy;

class GuardedByDemo {
  @GuardedBy("GuardedByDemo.class")
  static final long foo = 23;

  synchronized static void bar() {
    long a = foo;
  }
}