// "Replace with 'Integer ignored1'" "true-preview"
class Test {
  void test(Integer n) {
    switch (n) {
      case Integer ignored1:
        int ignored = 1;
        break;
    }
  }
}