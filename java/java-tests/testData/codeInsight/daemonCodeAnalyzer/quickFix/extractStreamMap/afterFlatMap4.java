// "Extract variable 's' to 'mapToObj' operation" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  void testFlatMap() {
      IntStream.of(1, 2, 3).mapToObj(String::valueOf).flatMapToInt(s -> IntStream.range(0, s.length())).forEach(System.out::println);
  }
}