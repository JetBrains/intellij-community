import java.util.Collection;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

class Test {
  public static <T> int sum(Collection<? extends T> collection, ToIntFunction<? super T> mapper) {
    return collection.stream().collect(Collectors.summingInt(mapper));
  }
}