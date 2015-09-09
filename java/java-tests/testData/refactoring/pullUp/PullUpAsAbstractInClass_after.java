abstract class A {
    public abstract void test();
}
class B extends A {
  @Override
  public final synchronized void test(){}
}