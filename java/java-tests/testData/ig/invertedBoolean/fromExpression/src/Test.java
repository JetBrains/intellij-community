class Test {
  boolean foo(){
    return false;
  }

  boolean foo1(boolean flag){
    return !foo();
  }

  void bar(){
    if (foo1(foo())){
      return;
    }
  }
}