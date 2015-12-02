import java.util.function.Function;

interface I<T>{
  void foo(Function<T, T> f);
}

class C {
  void bar(I<?> x) {
    x.foo(a -> {
      x.foo<error descr="'foo(java.util.function.Function<capture<?>,capture<?>>)' in 'I' cannot be applied to '(<lambda expression>)'">(y -> a)</error>;
      return a;
    });
  }
}