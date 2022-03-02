// "Inline 'map()' body into the next 'mapToInt()' call" "true"
import java.util.List;
import java.util.stream.IntStream;

public class Main {
  public static void test(List<CharSequence> list) {
    list.stream().mapToInt(cs -> ((String) cs).length()).asLongStream().map(x -> x*2).boxed().forEach(System.out::println);
  }
}