import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

  public <T, C extends Collection<T>> C copyInto1(Stream<T> fi, C collection) {
    return fi.collect(Collectors.toCollection(() -> collection));
  }


  public <T, C extends Collection<? super T>> C copyInto2(Stream<T> fi, C collection) {
    return fi.collect(Collectors.collectingAndThen(Collectors.toList(), ts -> {
        collection.addAll(ts);
        return collection;
    }));
  }
}