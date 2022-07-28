// "Replace with 'Integer ignored'" "true-preview"
class Test {
  void test(Integer n) {
    switch (n) {
      case Integer ignored: break;
    }
  }
}