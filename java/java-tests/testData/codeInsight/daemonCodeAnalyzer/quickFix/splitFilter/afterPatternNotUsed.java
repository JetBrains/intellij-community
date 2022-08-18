// "Split into filter chain" "true-preview"
import java.util.List;

class X {
  void test(List<?> list) {
    list.stream().filter(x -> x instanceof String s && !s.isEmpty()).filter(x -> x.hashCode() > 0).forEach(System.out::println);
  }
}