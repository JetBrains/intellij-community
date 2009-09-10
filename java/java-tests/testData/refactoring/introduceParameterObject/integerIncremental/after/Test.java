class Test {
  void foo(Integer integer) {
    integer.intValue() + 1;
  }

  void bar(){
    foo(new Integer(1));
  }
}