class Test {
  void f(boolean b){
    String [] s = {b <caret>? "a" : "c"};
  }
}