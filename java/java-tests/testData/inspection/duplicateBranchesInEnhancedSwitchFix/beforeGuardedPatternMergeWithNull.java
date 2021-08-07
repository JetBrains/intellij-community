// "Merge with 'case null'" "false"
class C {
  void foo(Object o) {
    switch (o) {
      case null -> bar("A");
      case String s -> bar("B");
      case Number n && n.intValue() == 42 -> <caret>bar("A");
      default -> bar("C");
    }
  }
  void bar(String s){}
}