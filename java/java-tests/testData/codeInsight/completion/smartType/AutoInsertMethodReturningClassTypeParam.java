abstract class K<T> {
  protected abstract T get();
  private void m() {
    T t = g<caret>
  }
}