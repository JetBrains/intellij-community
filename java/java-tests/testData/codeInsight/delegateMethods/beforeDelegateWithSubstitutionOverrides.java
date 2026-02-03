class A<T> {
  public T get(T key) {
    return null;
  }
}

class B<K> extends A<K> {
  private A<K> a;

  <caret>

}