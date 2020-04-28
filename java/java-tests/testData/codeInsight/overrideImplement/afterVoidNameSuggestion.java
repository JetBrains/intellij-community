interface I<T> {
  void m(T t);
}
class M extends I<Void> {
    public void m(Void unused) {
        
    }
}