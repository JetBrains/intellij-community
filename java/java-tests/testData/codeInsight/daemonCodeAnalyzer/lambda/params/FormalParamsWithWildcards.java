class Test {
  public interface I<K, V> {
    public V put(K k);
  }

  {
    final I<? super Long, CharSequence> i = (Number n) -> n.toString();
  }
}