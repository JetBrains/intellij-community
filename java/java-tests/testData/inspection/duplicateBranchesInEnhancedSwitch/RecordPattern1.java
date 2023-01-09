class C {
  void foo(Object o) {
    switch (o) {
      case Point(double x, double y) -> bar("A");
      case Number n -> bar("B");
      case null -> bar("A");
      default -> bar("C");
    }
  }
  void bar(String s){}
}

record Point(double x, double y) {
}