class A{
  private void foo(String s){}
  public void foo(Object o){}
}

class B{
  {
    new A().<ref>foo("a");
  }
}