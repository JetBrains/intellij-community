// "Make 'valueOf()' return 'int'" "false"
class X {
  void test() {
    int x = <caret>Y.valueOf("A");
  }
  
  
  enum Y {A, B, C}
}