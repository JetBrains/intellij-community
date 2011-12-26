// "Add constructor parameters" "true"
class A {
  private final int field;
  private final Object o;
  private final Runnable runnable;
  A(int field, Object o, Runnable runnable, String... strs) {
      this.field = field;
      this.o = o;
      this.runnable = runnable;
  }

}
