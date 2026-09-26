// "Remove redundant assignment" "true-preview"
class X {
  void test() {
    for (int i = 0; i < Integer.MAX_VALUE; ) {

    }
  }
}