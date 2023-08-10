class C {
  final String x;

  C() {
    <caret>x = "foo";
    Runnable runnable = () -> x.trim();
  }
}