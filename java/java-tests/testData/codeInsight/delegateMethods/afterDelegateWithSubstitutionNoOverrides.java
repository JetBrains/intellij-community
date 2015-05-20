class A<T> {
  public T get(T key) {
    return null;
  }
}

class B<K> extends A<K> {
  private A<String> a;

    public String get(String key) {
        return a.get(key);
    }
}