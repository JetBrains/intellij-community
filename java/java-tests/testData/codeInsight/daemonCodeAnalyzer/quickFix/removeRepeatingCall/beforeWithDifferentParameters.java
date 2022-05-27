// "Remove repeating call 'equals()'" "false"
class Test {
  void test(String s) {
    if (s.equals("foo").<caret>equals("bar")) {

    }
  }
}