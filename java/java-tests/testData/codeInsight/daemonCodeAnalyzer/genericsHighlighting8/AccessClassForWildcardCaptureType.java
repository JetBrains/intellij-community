
abstract class A {
  protected void bar(){}
}

abstract class Strange extends A {
  private void foo(){}

  static void fooBar(Class<? extends Strange> clazz){
    getInstance(clazz).<error descr="'foo()' has private access in 'Strange'">foo</error>();
    getInstance(clazz).bar();
  }

  public static <T> T getInstance(Class<T> clazz) {
    return (T) new StrangeImpl();
  }
}

class StrangeImpl extends Strange{}
