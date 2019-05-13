// "Replace lambda with method reference" "true"
class Example {
  {
    long[][] avg = collect(long[][]::new);
  }

  interface P<T> {
    T _(int i);
  }

  <T> T collect(P<T> p) {
    return null;
  }
}