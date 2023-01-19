// "Split into filter chain" "false"
import java.util.List;

class X {
  void test(List<?> list) {
    list.stream().filter(x -> x instanceof String s &<caret>& !s.isEmpty()).forEach(System.out::println);
  }
}