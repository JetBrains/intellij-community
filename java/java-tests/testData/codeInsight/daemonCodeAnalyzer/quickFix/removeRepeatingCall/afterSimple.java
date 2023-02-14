// "Remove repeating call 'hashCode()'" "true-preview"
class Test {
  void test(Object obj) {
    int x = obj.hashCode();
  }
}