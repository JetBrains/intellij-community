class Test {
  static class TKey<T> {
  }

  public interface Getter {
    <T> T getValue(TKey<T> key);
  }

  static final TKey<Boolean> KEY_B = new TKey<>();

  public static void f(Getter getter) {
    String name = getter.getValue(KEY_B) ? "foo" : "bar";
  }
}
