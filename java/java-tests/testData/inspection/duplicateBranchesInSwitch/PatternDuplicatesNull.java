class C {
  void foo(Object o) {
    switch (o) {
      case null:
        bar("A");
        break;
      case String s:
        bar("B");
        break;
      case Number n:
        bar("A");
        break;
      default:
        bar("C");
        break;
    }
  }
  void bar(String s){}
}