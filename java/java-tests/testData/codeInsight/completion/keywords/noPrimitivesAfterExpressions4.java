import java.util.stream.IntStream;
import java.util.stream.Stream;

public class AA {
  IntStream distinct2(Stream<Object> a) {
    return a.map(i-> call(null) && i in<caret>
  }
}