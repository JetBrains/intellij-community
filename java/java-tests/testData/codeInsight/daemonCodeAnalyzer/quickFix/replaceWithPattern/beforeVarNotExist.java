// "Replace with 'Integer ignored'" "true"
class Test {
  void test(Integer n) {
    switch (n) {
      case Integer<caret>: break;
    }
  }
}