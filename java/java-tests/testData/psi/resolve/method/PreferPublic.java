class A{
  private void foo(String s){}
  public void foo(Object o){}
}

class B{
  {
    new A().<caret>foo("a");
  }
}