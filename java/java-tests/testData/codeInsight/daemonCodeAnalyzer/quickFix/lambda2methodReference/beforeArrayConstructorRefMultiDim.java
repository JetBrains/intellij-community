// "Replace lambda with method reference" "true-preview"
class Example {
  {
    long[][] avg = collect((int i) -> new <caret>long[i][]);
  }

  interface P<T> {
    T _(int i);
  }

  <T> T collect(P<T> p) {
    return null;
  }
}