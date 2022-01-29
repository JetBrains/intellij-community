// "Merge 'mapToInt()' call and 'asLongStream()' call" "true"
import java.util.List;
import java.util.stream.IntStream;

public class Main {
  public static void test(List<CharSequence> list) {
    list.stream().map(cs -> (String)cs).mapToLong(s -> (long) s.length()).map(x -> x*2).boxed().forEach(System.out::println);
  }
}