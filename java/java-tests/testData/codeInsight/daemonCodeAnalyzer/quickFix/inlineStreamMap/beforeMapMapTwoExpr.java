// "Inline 'map()' body into the next 'map()' call" "false"
import java.util.List;

public class Main {
  public static void test(List<CharSequence> list) {
    list.stream().m<caret>ap(cs -> {
      cs = cs.subSequence(0, 10);
      return cs.subSequence(1, 5);
    }).map(cs -> cs.length()).forEach(System.out::println);
  }
}