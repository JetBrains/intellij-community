// "Inline 'map()' body into the next 'forEach()' call" "true-preview"
import java.util.List;

public class Main {
  public static void test(List<String> list) {
    list.stream().forEach(s -> System.out.println(s.trim()));
  }
}