public abstract class FixClassAndInterface extend<caret>s A {
@Override
public void m1() {}
  }

abstract class A {
  abstract void m1();

  abstract void m2();
}

abstract class B extends A {
  void m1() {

  };

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