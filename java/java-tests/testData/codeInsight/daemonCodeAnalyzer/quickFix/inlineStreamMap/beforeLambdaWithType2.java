// "Inline 'map()' body into the next 'forEach()' call" "true"
import java.util.List;

public class Main {
  public static void test(List<String> list) {
    list.stream().ma<caret>p(s -> s.trim()).forEach((String x) -> System.out.println(x));
  }
}