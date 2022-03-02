// "Inline 'map()' body into the next 'map()' call" "true"
import java.util.List;

public class Main {
  public static void test(List<CharSequence> list) {
    list.stream().m<caret>ap(cs -> {
      /*out of body*/ return cs/*in body*/.subSequence(1, 5);
    }).map(cs -> cs.length()).forEach(System.out::println);
  }
}