import java.util.Optional;
import java.util.function.Function;

class Calls {
  <A> Optional<A> a(A a) {return null;}
  <B> B b(Optional<B> a) {return null;}
  <C> Optional<C> c(C a) {return null;}

  void foo(Function<String, Optional> computable) {}

  {
    <error descr="Not a statement">((x) -> a(b(c(x))));</error>
  }
}
