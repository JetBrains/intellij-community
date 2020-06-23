interface I<T> {
  void m(T t);
}
class M extends I<Void> {
  <caret>
}