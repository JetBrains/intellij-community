class C {
  final String x;

  C() {
      String x1 = x;
      x1 = "foo";
    Runnable runnable = () -> x.trim();
  }
}