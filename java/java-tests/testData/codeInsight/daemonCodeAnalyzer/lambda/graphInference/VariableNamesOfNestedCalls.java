
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class BigNumber {
  public <U, V> void toTypeMap(Stream<U> p) {
    Function<U, V> map2 = p.collect(Collectors.toMap(Function.identity(<caret>), Function.identity()));
  }
}