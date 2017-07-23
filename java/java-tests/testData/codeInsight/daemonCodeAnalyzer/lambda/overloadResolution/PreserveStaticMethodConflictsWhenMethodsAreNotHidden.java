import java.util.function.Function;
import java.util.function.IntFunction;

class A {
  static void foo(Function<String, String> f) {}
}

class B extends A {
  static void foo(IntFunction<String> f) {}

  public static void main(String[] args) {
    <error descr="Ambiguous method call: both 'B.foo(IntFunction<String>)' and 'A.foo(Function<String, String>)' match">foo</error>(a -> "1");
  }
}