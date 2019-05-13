import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// "Extract Set from comparison chain" "true"
public class Test {
    private static final Set<String> NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("foo", "bar", "baz")));

    void testOr(String name) {
    if(name == null || NAMES.contains(name)) {
      System.out.println("foobarbaz");
    }
  }
}
