class X {
  void <warning descr="Multiple methods named 'foo' with the same number of parameters">foo</warning>(Object o) {}
  void <warning descr="Multiple methods named 'foo' with the same number of parameters">foo</warning>(int i) {}

  void bar(String s) {}
  void bar(Integer i) {}
}