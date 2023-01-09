class C {
  void foo(Object o) {
    switch (o) {
      case Point(double x, double y) point when y > x -> bar("A");
      case Number n -> bar("B");
      default -> bar("C");
      case null -> bar("A");
    }
  }
  void bar(String s){}
}

record Point(double x, double y) {
}