// "Add constructor parameters" "true"
public class A {
  private final int field;
  private final Object o;
  private final Runnable runnable;

    public A(int field, Object o, Runnable runnable) {
        this.field = field;
        this.o = o;
        this.runnable = runnable;
    }
}
