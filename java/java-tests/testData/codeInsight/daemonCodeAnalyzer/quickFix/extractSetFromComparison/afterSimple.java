import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// "Extract Set from comparison chain" "true"
public class Test {
    private static final Set<String> S = new HashSet<>(Arrays.asList("foo", "bar", "baz"));

    void testOr(String s) {
    if(S.contains(s)) {
      System.out.println("foobarbaz");
    }
  }
}
