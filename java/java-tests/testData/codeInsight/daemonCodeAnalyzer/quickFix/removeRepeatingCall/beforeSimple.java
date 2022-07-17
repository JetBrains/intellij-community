// "Remove repeating call 'hashCode()'" "true"
class Test {
  void test(Object obj) {
    int x = obj.hashCode().<caret>hashCode();
  }
}