class C {
  String x;

  C() {
    <caret>x = "foo";
    Runnable runnable = () -> x.trim();
  }
}