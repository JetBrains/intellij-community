// "Convert to local variable" "true"
class Temp {

  void test(int <caret>p) {
    p = 1;
    System.out.print(p);
  }
}