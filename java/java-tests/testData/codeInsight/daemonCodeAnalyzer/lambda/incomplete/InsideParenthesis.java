import java.util.Optional;
import java.util.function.Function;

class Calls {
  <A> Optional<A> a(A a) {return null;}
  <B> B b(Optional<B> a) {return null;}
  <C> Optional<C> c(C a) {return null;}

  void foo(Function<String, Optional> computable) {}

  {
    ((x) -> a(b(c<error descr="'c(java.lang.Object)' in 'Calls' cannot be applied to '(<lambda parameter>)'">(x)</error>)));
  }
}
