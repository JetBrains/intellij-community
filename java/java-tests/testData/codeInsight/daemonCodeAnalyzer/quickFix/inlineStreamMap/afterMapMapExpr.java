// "Inline 'map()' body into the next 'map()' call" "true"
import java.util.List;

public class Main {
  public static void test(List<CharSequence> list) {
      /*out of body*/
      list.stream().map(cs -> cs/*in body*/.subSequence(1, 5).length()).forEach(System.out::println);
  }
}