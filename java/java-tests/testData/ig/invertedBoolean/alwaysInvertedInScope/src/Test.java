class Test {
  boolean foo(){
    return false;
  }

  void bar(){
    if (!foo()){
      return;
    }
  }   
}