// "Add constructor parameters" "true"
class A {
  private final int field;
  private final Object o;
  private final Runnable runnable;

    public A(Runnable runnable, Object o, int field) {
        this.runnable = runnable;
        this.o = o;
        this.field = field;<caret>
    }
}
