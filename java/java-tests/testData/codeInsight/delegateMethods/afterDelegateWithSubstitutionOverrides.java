class A<T> {
  public T get(T key) {
    return null;
  }
}

class B<K> extends A<K> {
  private A<K> a;

    @Override
    public K get(K key) {
        return a.get(key);
    }
}