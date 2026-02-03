import java.util.function.Supplier;

class Test {
  public static void casts(Runnable e) {
    Object o = (Supplier<?  extends Runnable>) () -> e;
  }
}