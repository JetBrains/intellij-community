import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

class TypeDetectionTest {

  public static void main(String[] args) {
    List<Number> numbers = Stream.of(1, 2).collect(toList());
  }

}