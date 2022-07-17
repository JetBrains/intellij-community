// "Remove repeating call 'equals()'" "true"
class Test {
  void test(String s) {
    if (s.equals("foo").<caret>equals("foo")) {

    }
  }
}