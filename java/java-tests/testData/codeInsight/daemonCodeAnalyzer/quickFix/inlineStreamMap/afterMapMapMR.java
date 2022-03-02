// "Inline 'map()' body into the next 'map()' call" "true"
import java.util.List;

public class Main {
  public static void test(List<CharSequence> list) {
    list.stream().map(cs -> cs.subSequence(1, 5).length()).forEach(System.out::println);
  }
}