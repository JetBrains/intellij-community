class C {
  final String x;

  C() {
    x = "foo";
    Runnable runnable = () -> {
        String x = this.x;
        x.trim();
    };
  }
}