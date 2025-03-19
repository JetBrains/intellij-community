// "Fix all 'Missing '@Override' annotation' problems in file" "true"
class Super {
  void test() {}
  void test2() {}
}
class Child extends Super {
  @Override
  void test() {}
  @Override
  void test2() {}
}