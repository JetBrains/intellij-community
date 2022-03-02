// "Inline 'map()' body into the next 'map()' call" "true"
import java.util.List;

public class Main {
  public static void test(List<CharSequence> list) {
      /*before dot*/
      /*after dot*/
      list.stream()
      /*before dot2*/./*after dot2*/map(cs -> /*length!!!*/ cs.subSequence(/*subsequence*/1, 5).length()).forEach(System.out::println);
  }
}