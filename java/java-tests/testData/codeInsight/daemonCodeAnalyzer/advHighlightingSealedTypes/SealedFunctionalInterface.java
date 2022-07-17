<error descr="Functional interface can't be declared as 'sealed'">@FunctionalInterface</error>
sealed interface I {
  void m();
}
sealed interface Fn {
  int get();
}

final class MyFn implements Fn {
  @Override
  public int get() {
    return 0;
  }
}

class Test {

  void test() {
    Fn fn = <error descr="Sealed class can not be used as functional interface">() -> 1</error>;
    Fn fn1 = <error descr="Sealed class can not be used as functional interface">this::doSmth</error>;
    foo(<error descr="Sealed class can not be used as functional interface">() -> 1</error>);
    foo(<error descr="Sealed class can not be used as functional interface">this::doSmth</error>);
  }
  
  int doSmth() {
    return 0;
  }

  void foo(Fn fn) { }
}