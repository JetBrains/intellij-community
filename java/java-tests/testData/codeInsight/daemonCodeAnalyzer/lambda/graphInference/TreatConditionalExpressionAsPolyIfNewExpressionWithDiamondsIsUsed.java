class FImpl<K> {
  public FImpl(K k) {
  }

  public void myMethod() {
    foo (false ? new FImpl<>(null) : null);
  }

  private <T> T foo(final T bar) {
    return null;
  }
  private <T> T foo(final String bar) {
    return null;
  }
}
