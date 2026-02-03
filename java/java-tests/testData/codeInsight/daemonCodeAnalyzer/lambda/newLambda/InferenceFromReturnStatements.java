
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

class SameCalls<ST> {
  <B> List<B> bar(B a) {return null;}
  <R> Optional<R> foo(Function<String, Optional<R>> computable) {return null;}

  List<String> ff(SameCalls<String> sc){
    return sc.foo((x) -> {
      return Optional.of(bar(x));
    }).get();
  }


}
