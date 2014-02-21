import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Stream;


class TypeDetectionTest {

  void main(Stream<Integer> of) {
    of.sorted(comparing(n -> n.doubleValue()));
  }

  public static <T, U extends Comparable<? super U>> Comparator<T> comparing(Function<? super T, ? extends U> keyExtractor){
    return null;
  }
}

