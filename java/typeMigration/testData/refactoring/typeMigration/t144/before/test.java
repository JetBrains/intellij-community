class Test {
  public static void test() {
    Long n = new AnInterface2<Long,Void>() {
      @Override
      public Long invoke(Void v) {
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