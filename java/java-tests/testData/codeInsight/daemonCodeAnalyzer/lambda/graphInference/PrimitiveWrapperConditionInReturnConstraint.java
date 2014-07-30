class Test {
  static class TKey<T> {
  }

  public interface Getter {
    <T> T getValue(TKey<T> key);
  }

  static final TKey<Integer> KEY_I = null;


  public static void f(Getter getter, TKey<Integer> key) {
    double d1 = getter.getValue (key);
  }
}