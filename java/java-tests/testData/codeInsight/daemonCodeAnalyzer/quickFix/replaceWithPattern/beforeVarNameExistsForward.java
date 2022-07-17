// "Replace with 'Integer ignored1'" "true"
class Test {
  void test(Integer n) {
    switch (n) {
      case Integer<caret>:
        int ignored = 1;
        break;
    }
  }
}