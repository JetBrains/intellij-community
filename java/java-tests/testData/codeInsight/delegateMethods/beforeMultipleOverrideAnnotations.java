interface B0 {
  void foo();
}

class A3 implements B0{
  @Override
  public void foo() {}
}

class B3 implements A3 {
  B3 myDelegate;

  <caret>
}
