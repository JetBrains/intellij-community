import java.util.function.*;

class Test {
  void foo(Function<String, String> f) {}
  void foo(String f) {}

  {
    foo(a -> {
      String s = a.substring(0);
    <error descr="Missing return statement">}</error>);
  }

  interface A {
    void m(String s);
  }

  void bar(Function<String, String> f) {}
  void bar(A f) {}

  {
    bar(a -> {
      String s = a.substring(0);
    });
  }
}