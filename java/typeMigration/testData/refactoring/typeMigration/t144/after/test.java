class Test {
  public static void test() {
    String n = new AnInterface2<String, Void>() {
      @Override
      public String invoke(Void v) {
        return null;
      }
    }.invoke(null);
  }

  public interface AnInterface<T, V> {
    T invoke(V v);
  }

  public interface AnInterface2<T, V> extends AnInterface<T, V> {
  }
}