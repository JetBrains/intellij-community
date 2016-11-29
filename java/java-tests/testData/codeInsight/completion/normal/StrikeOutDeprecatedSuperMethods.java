class Super {
  void foo1() {}
  @Deprecated
  void foo2() {}
}

class Sub extends Super {
  foo<caret>
}