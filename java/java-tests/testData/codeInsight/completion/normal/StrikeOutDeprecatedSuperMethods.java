class Super {
  void foo1() {}
  @Deprecated
  void foo2(int x) {}
}

class Sub extends Super {
  foo<caret>
}