// "Use existing implementation of 'foo'" "true"
interface I {
    void foo();
}
class IImpl implements I {
  @Override
  public void foo() {}
}

interface II extends I {}
class IImpl2 extends IImpl implements II {
    public void foo() {}
}