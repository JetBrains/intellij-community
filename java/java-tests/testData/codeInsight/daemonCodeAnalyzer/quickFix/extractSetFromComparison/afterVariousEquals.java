// "Extract Set from comparison chain" "true"
import java.util.*;

public class Test {
    private static final Set<String> S = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("foo", "bar", "baz", "quz")));

    void testOr(String s) {
    if(Objects.equals(s, null) || S.contains(s)) {
      System.out.println("foobarbaz");
    }
  }
}
