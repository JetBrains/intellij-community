class Test {
  public static void test() {
    String n = new AnInterface<String, Void>() {
      @Override
      public String invoke(Void v) {
        return null;
      }
    }.invoke(null);
  }

  public static interface AnInterface<T, V> {
    T invoke(V v);
  }
}