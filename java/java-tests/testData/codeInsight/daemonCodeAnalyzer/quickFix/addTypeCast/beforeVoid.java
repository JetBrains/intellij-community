// "Cast to 'void'" "false"
class Test {
  void test() {
    Runnable r = () -> (System.out.<caret>println());
  }
}