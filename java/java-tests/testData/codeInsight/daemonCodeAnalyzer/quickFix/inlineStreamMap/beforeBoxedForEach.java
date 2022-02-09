// "Merge 'boxed()' call and 'forEach()' call" "true"
import java.util.List;
import java.util.stream.IntStream;

public class Main {
  public static void test(List<CharSequence> list) {
    list.stream().map(cs -> (String)cs).mapToInt(String::length).asLongStream().map(x -> x*2).bo<caret>xed().forEach(System.out::println);
  }
}