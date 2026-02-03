interface I {
  void foo(int i);
}

class A implements I {
  @Override
  public void foo(int <warning descr="Parameter 'p' is never used"><caret>p</warning>) {
  }
}