interface F {
  void foo(int myI);
}

class A implements F {
  int myI;
  public void foo(int myI){
    this.myI = myI;
  }
}
