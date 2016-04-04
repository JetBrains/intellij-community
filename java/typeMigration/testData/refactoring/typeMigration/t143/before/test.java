class Test {
  public static void test() {
    Long n = new AnInterface<Long,Void>() {
      @Override
      public Long invoke(Void v) {
        return null;
      }
    }.invoke(null);
  }

  public static interface AnInterface<T, V> {
    T invoke(V v);
  }
}