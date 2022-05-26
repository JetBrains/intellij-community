// "Inline 'mapToInt()' body into the next 'flatMap()' call" "true"
import java.util.List;
import java.util.stream.IntStream;

public class Main {
  public static void test(List<CharSequence> list) {
    list.stream().map(cs -> (String)cs).flatMapToInt(s1 -> IntStream.range(0, s1.length())).forEach(System.out::println);
  }
}