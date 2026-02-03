class Test {
  void foo(int i<caret>) {
    System.out.println(i);
  }
  void bar(){foo();}
}