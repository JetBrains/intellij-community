import java.util.Set;

public class BoxedDivisionComparison {
  void test(long a, Set<Long> set) {
    String s = null;
    for (Long b : set) {
      if (a / b < 10) {
        s = "foo";
        break;
      }
    }
    if (s == null) {}
  }
}