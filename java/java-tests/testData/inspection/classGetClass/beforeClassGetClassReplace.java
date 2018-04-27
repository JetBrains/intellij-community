// "Replace with 'Class.class'" "true"
class Test {
  void test(Class<?> clazz) {
    System.out.println(clazz.getCl<caret>ass().getName());
  }
}