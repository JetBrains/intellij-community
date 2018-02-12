public abstract class FixClassAndClass extends B {
}

interface A {
  void m1();

  void m2();
}

abstract class B implements A {
  @Override
  public void m1() {}
}

class C1 extends B {
  @Override
  public void m2() {

  }
}
class C2 extends B {
  @Override
  public void m2() {

  }
}
class C3 extends B {
  @Override
  public void m2() {

  }
}
class C4 extends B {
  @Override
  public void m2() {

  }
}
class C5 extends B {
  @Override
  public void m2() {

  }
}