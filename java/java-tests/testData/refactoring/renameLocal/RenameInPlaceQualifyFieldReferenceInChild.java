interface F {
  void foo(int <caret>i);
}

class A implements F {
  int myI;
  public void foo(int i){
    myI = i;
  }
}
