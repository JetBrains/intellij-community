class C {
  String x;

  C() {
      String x = this.x;
      x = "foo";
    Runnable runnable = () -> x.trim();
  }
}