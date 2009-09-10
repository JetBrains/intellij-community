class Test {
  void foo(Integer integer) {
    if (integer.intValue() == 0) {
    }
  }

  void bar(){
    foo(new Integer(1));
  }
}