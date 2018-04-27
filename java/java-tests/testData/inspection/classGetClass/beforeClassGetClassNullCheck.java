// "Remove 'getClass()' call" "false"
class Test {
  void test(Class<?> clazz) {
    // implicit null check
    clazz.getCla<caret>ss();
    System.out.println(clazz.getName());
  }
}