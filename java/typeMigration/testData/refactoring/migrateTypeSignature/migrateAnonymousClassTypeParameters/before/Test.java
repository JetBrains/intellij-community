public class Test {
  public static void test() {
    Number n = new Interface<Number, Void>() {
      @Override
      public Number invoke(Void v) {
        return null;
      }
    }.invoke(null);
  }

  public interface Interface<T, V> {
    T invoke(V v);
  }
}
