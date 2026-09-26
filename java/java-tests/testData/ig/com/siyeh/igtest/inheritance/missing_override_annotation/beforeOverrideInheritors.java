// "Annotate overriding methods with '@Override'" "true"
class Super {
  void te<caret>st() {}
}
class Child extends Super {
  void test() {}
}
class Child2 extends Super {
  void test() {}
}