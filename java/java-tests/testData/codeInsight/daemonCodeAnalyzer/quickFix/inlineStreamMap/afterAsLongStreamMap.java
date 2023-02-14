// "Merge 'asLongStream()' call and 'map()' call" "true-preview"
import java.util.List;
import java.util.stream.IntStream;

public class Main {
  public static void test(List<CharSequence> list) {
    list.stream().map(cs -> (String)cs).mapToInt(String::length).mapToLong(i -> (long) i * 2).boxed().forEach(System.out::println);
  }
}