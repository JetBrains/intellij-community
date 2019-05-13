
abstract class AbstractBean<B extends AbstractBean<B>>  {

  abstract B foo();
  abstract void bar();

  private void bar1() {}

  private void getB(AbstractBean<? extends B> bean, B b) {
    bean.foo().bar();
    bean.foo().<error descr="'bar1()' has private access in 'AbstractBean'">bar1</error>();
  }
}