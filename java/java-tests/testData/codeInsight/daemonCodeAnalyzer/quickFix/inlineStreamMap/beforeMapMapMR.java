// "Inline 'map()' body into the next 'map()' call" "true"
import java.util.List;

public class Main {
  public static void test(List<CharSequence> list) {
    list.stream().m<caret>ap(cs -> cs.subSequence(1, 5)).map(CharSequence::length).forEach(System.out::println);
  }
}