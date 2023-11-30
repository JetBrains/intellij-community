// "Annotate overriding methods with '@Override'" "true"
class Super {
  void test() {}
}
class Child extends Super {
  @Override
  void test() {}
}
class Child2 extends Super {
  @Override
  void test() {}
}