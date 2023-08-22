// "Merge 'boxed()' call and 'forEach()' call" "true-preview"
import java.util.List;
import java.util.stream.IntStream;

public class Main {
  public static void test(List<CharSequence> list) {
    list.stream().map(cs -> (String)cs).mapToInt(String::length).asLongStream().map(x -> x*2).forEach(l -> System.out.println((Long) l));
  }
}