interface I2 {
  void m();
}

abstract class C1 {
  public abstract void m();
}

class C3 extends C1 implements I2 {
  @Override
  public void m() {}
}