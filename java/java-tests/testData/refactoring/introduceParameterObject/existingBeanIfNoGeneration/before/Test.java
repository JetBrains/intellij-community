class Test {
  void foo(int i) {
    if (i == 0) {
      i++;
    }
  }

  void bar(){
    foo(1, 2);
  }
}