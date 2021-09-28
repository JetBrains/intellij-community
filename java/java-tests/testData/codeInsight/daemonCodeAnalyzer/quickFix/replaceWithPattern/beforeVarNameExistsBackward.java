// "Replace with 'Integer ignored1'" "true"
class Test {
  void test(Integer n, int ignored) {
    switch (n) {
      case Integer<caret>: break;
    }
  }
}