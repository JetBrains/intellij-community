import java.util.function.Function;
import java.util.function.IntFunction;

class A {
  static void foo(Function<String, String> f) {}
}

class B extends A {
  static void foo(IntFunction<String> f) {}

  public static void main(String[] args) {
    foo<error descr="Ambiguous method call: both 'B.foo(IntFunction<String>)' and 'A.foo(Function<String, String>)' match">(a -> "1")</error>;
  }
}