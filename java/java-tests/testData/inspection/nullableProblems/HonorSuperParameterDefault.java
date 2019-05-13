import foo.*;

class B {
  {
    new NullableFunction() {
      public void fun(Object o) {}
    };
    new AnyFunction() {
      public void fun(Object <warning descr="Not annotated parameter overrides @ParametersAreNonnullByDefault parameter">o</warning>) {}
    };
  }
}