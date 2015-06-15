import java.util.function.Function;

interface I<T>{
  void foo(Function<T, T> f);
}

class C {
  void bar(I<?> x) {
    x.foo(a -> {
      x.foo(y -> <error descr="Bad return type in lambda expression: capture of ? cannot be converted to capture of ?">a</error>);
      return a;
    });
  }
}