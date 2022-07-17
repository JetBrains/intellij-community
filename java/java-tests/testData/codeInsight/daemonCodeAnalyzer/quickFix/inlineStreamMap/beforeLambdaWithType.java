// "Inline 'map()' body into the next 'forEach()' call" "true"
import java.util.List;

public class Main {
  public static void test(List<String> list) {
    list.stream().ma<caret>p((String s) -> s.trim()).forEach(x -> System.out.println(x));
  }
}