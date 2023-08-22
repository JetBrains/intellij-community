// "Replace '\s' sequences with spaces" "true-preview"
class X {
  void test(String str) {
    if (str.matches("\uuu1234<caret>\s+")) {

    }
  }
}