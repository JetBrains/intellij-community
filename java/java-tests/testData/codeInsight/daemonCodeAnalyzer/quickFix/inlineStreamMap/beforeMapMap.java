// "Inline 'map()' body into the next 'map()' call" "true"
import java.util.List;

public class Main {
  public static void test(List<CharSequence> list) {
    list.stream()/*before dot*/./*after dot*/m<caret>ap(cs -> cs.subSequence(/*subsequence*/1, 5))
      /*before dot2*/./*after dot2*/map(cs -> /*length!!!*/ cs.length()).forEach(System.out::println);
  }
}