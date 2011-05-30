// "Add constructor parameters" "true"
class A {
  private final int field;
  private final Object o;
  private final Runnable runnable;
  A(int field, Runnable runnable, Object o, String... strs) {
      this.field = field;<caret>
      this.runnable = runnable;
      this.o = o;
  }

}
