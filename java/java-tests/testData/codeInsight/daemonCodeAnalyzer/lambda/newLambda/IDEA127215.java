
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;


abstract class FooBar<M> {

  abstract void collect(List<? extends M> collector);

  void foo(FooBar<?> objectStream) {
    objectStream. collect(toList( ));

  }

  static <T> List<T> toList() {
    return null;
  }

}
class Test {
  <T> List<List<Object>> foo(List<T> objects, Function<T, ?>... functions) {
    return <error descr="Incompatible types. Required List<List<Object>> but 'collect' was inferred to R:
no instance(s) of type variable(s)  exist so that List<capture of ?> conforms to List<Object>
inference variable T has incompatible bounds:
 equality constraints: List<Object>
lower bounds: List<capture of ?>">objects.stream()
      .map(object -> Arrays.stream(functions)
        .map(fn -> fn.apply(object))
        .collect(toList()))
      .collect(toList());</error>
  }
}