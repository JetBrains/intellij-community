// "Replace with toArray" "true"
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Test {
  String[] test(int count) {
      return IntStream.range(0, count).mapToObj(i -> Stream.of("one", "two", "three")).flatMap(Function.identity()).toArray(String[]::new);
  }
}
