// "Inline 'mapToInt()' body into the next 'flatMap()' call" "true"
import java.util.List;
import java.util.stream.IntStream;

public class Main {
  public static void test(List<CharSequence> list) {
    list.stream().map(cs -> (String)cs).ma<caret>pToInt(String::length).flatMap(s -> IntStream.range(0, s)).forEach(System.out::println);
  }
}