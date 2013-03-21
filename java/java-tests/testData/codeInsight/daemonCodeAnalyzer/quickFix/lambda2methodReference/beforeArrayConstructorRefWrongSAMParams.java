// "Replace lambda with method reference" "false"
class Example {
  {
    long[] avg = collect(() -> new <caret>long[]);
  }

  interface P<T> {
    T _();
  }

  <T> T collect(P<T> p) {
    return null;
  }
}