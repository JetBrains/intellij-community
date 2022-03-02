// "Inline 'map()' body into the next 'mapToInt()' call" "true"
import java.util.List;
import java.util.stream.IntStream;

public class Main {
  public static void test(List<CharSequence> list) {
    list.stream().m<caret>ap(cs -> (String)cs).mapToInt(String::length).asLongStream().map(x -> x*2).boxed().forEach(System.out::println);
  }
}