import java.util.function.Function;

class Test<A> {
  class P {
    {
      g(P::<error descr="Cannot resolve method 'aa'">aa</error>);
    }
  }
  <R> void g(Function<R, String> r){}
}