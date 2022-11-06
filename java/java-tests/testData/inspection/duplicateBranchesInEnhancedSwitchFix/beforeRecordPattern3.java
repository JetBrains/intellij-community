// "Merge with 'case null'" "false"
class C {
  void foo(Object o) {
    switch (o) {
      case null -> bar("A");
      case Point(double x, double y) point -> ba<caret>r("A");
      case Number n -> bar("B");
      default -> bar("C");
    }
  }
  void bar(String s){}
}

record Point(double x, double y) {
}