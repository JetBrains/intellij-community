class SuperClass {
  boolean foo(){
    return false;
  }

  void bar(){
    if (!foo()){
      return;
    }
  }

  int bah() {
    return bah();
  }
}
class Test extends SuperClass{
  boolean foo(){
    return super.foo();
  }
}