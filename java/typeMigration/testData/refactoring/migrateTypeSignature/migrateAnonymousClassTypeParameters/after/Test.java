public class Test {
  public static void test() {
    String n = new Interface<String, Void>() {
      @Override
      public String invoke(Void v) {
        return null;
      }
    }.invoke(null);
  }

  public interface Interface<T, V> {
    T invoke(V v);
  }
}
