class Test {
  boolean foo(){
    return false;
  }
  
  void bar(){
    if (!foo()){
      return;
    }
    System.out.println(!foo());
  }

  int bah() {
    return bah();
  }
}