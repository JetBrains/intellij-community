import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// "Extract Set from comparison chain" "true"
public interface Test {
    Set<String> S = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("foo", "bar", "baz")));

    default void testOr(String s) {
    if(S.contains(s)) {
      System.out.println("foobarbaz");
    }
  }
}
