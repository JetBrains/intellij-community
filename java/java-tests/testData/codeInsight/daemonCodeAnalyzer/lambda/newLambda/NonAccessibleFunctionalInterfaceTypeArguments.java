
import java.util.function.Consumer;

class Test {
  {
    consume(<error descr="'Outer.A' has private access in 'Outer'">o -> {}</error>, new Outer.B(), new Outer.C());
    consume(<error descr="'Outer.A' has private access in 'Outer'">Test::foo</error>, new Outer.B(), new Outer.C());
  }

  private static void foo(Object o) {}
  private static <T> void consume(Consumer<T> c, T... element) {}
}

class Outer {
  private static class A {}
  public static class B extends A {}
  public static class C extends A {}
}