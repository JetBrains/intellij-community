class Test {
  boolean foo(){
    return false;
  }
  
  void bar(){
    if (!foo()){
      return;
    }
  }

  int bah() {
    System.out.println(!foo());
    return bah();
  }
}