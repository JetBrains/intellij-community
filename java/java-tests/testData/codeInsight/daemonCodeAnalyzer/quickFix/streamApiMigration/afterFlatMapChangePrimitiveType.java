// "Replace with count()" "true"
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class Test {
  public void test() {
      long count = IntStream.range(0, 10).mapToObj(i -> LongStream.range(0, i)).flatMapToLong(Function.identity()).mapToObj(l -> Stream.of("x", "y", "z")).flatMap(Function.identity()).flatMapToInt(s -> IntStream.range(0, s.length())).count();
      System.out.println(count);
  }
}
