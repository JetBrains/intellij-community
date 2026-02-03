// "Fix all 'Explicit min/max check' problems in file" "false"
class Test {

  void test(int a, int b) {
    int c = <caret>a++ > b ? a : b++;
  }

}