class Executor<E> {
  <K> void bar(Executor<K> e) {
    Runnable r = () -> foo(e);
  }

  private <T> T foo(final Executor<T> e) {
    return null;
  }
}
