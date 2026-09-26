import java.util.function.*;

class A {
  <T> void f(T a, Consumer<T> b) { }

  void g(Object o) {
    f((String) o, (t) -> t.charAt(0));
  }
}

class Foo {

  class Base { public int sub; }

  class Sub1 extends Base { }

  class Sub2 extends Base { }

  public <T> void doSomething(T thing, BiConsumer<String, T> action) {
    action.accept("HELLO", thing);
  }

  private Sub1 s1;
  private Sub2 s2;

  public void foo(Base base) {
    if (base.sub == 1)
      doSomething((Sub1) base, (s, b) -> { System.out.println(s); this.s1 = b; });
    if (base.sub == 2)
      doSomething((Sub2) base, (s, b) -> { System.out.println(s); this.s2 = b; });
  }

}

class UnnecessaryCastError {

  public static void main(String args[]) {
    Env<String> env = new Env<>();

    map((String) env.getArgument("to"), x -> parse(x));
  }

  public static <T, U> U map(T t, Function<T, U> mapper) {
    return null;
  }

  public static String parse(String text) {
    return null;
  }

  static class Env<T> {
    <T> T getArgument(String s) {
      return (<warning descr="Casting 'null' to 'T' is redundant">T</warning>) null;
    }
  }
}


