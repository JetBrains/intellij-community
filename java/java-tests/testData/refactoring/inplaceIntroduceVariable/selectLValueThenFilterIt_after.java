class C {
  String x;

  C() {
      String x1 = x;
      x1 = "foo";
    Runnable runnable = () -> x1.trim();
  }
}