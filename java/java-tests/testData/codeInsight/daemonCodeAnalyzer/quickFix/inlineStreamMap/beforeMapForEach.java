// "Inline 'map()' body into the next 'forEach()' call" "true"
import java.util.List;

public class Main {
  public static void test(List<CharSequence> list) {
    ((list.stream().map(cs -> cs.subSequence(1, 5))).ma<caret>p(CharSequence::length)).forEach(System.out::println);
  }
}