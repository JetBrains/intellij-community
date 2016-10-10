class A {

  void foo() {
    new IterHelper<>().loopMap((k, val) -> {
      //do something
    });
  }
}

final class IterHelper<K, V> {
  public void loopMap(final MapIterCallback<K, V> callback) {
    //do something
  }
  public static interface MapIterCallback<K, V> {
    abstract void eval(K k, V v);
  }
}