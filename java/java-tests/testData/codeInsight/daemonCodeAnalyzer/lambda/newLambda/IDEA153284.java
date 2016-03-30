import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  public void validRedSquiggles(Stream<List<Integer>> stream) {
    final Iterator<Integer> x = concat(stream
                                         .map(cs -> cs.iterator())
                                         .collect(Collectors.toList()).iterator());
    final Iterator<Integer> x1 = concat(stream
                                          .map(List::iterator)
                                          .collect(Collectors.toList()).iterator());
  }

  public static <T> Iterator<T> concat(final Iterator<? extends Iterator<? extends T>> inputs) {
    return null;
  }
}