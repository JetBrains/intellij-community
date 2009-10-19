class A{
  void foo(Object a){}
  void foo(Object a, int b){}

  {
   foo(this<caret>
  }
}