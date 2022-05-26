// "Merge 'map()' call and 'boxed()' call" "true"
import java.util.List;
import java.util.stream.IntStream;

public class Main {
  public static void test(List<CharSequence> list) {
    list.stream().map(cs -> (String)cs).mapToInt(String::length).asLongStream().mapToObj(x -> (Long) (x * 2)).forEach(System.out::println);
  }
}