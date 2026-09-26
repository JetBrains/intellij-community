class Test{

  public void t() {
    I i1 = (Integer <warning descr="Parameter 'i' can have 'final' modifier">i</warning>) -> {};
    I i2 = i -> {};
  }
}

interface I {
  void f(Integer i);
}