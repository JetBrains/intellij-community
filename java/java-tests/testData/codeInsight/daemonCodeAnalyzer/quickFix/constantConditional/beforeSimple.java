// "Simplify" "true"
class Test {
  int test(int a, int b) {
    return true<caret>?a:b;
  }
}