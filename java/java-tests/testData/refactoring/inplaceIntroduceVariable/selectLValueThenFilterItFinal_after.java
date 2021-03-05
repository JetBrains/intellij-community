class C {
  final String x;

  C() {
    x = "foo";
      String x = this.x;
      Runnable runnable = () -> this.x.trim();
  }
}