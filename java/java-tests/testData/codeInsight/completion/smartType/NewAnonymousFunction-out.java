interface Function<X, Y> { }
class A {
  private static final Function<String, String> a = new Function<String, String>() {
  <caret>};
}