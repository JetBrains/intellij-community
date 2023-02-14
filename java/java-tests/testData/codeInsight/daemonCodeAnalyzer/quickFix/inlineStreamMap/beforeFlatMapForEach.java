// "Inline 'flatMap()' body into the next 'forEach()' call" "false"
import java.util.List;
import java.util.stream.IntStream;

public class Main {
  public static void test(List<CharSequence> list) {
    list.stream().map(cs -> (String)cs).mapToInt(String::length).fl<caret>atMap(s -> IntStream.range(0, s)).forEach(System.out::println);
  }
}