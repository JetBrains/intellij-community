import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// "Extract Set from comparison chain" "true"
public class Test {
  public static final String BAR = "bar";
    private static final Set<String> PROPERTIES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("foo", BAR, "baz")));

    void testOr(int i, String property) {
    int PROPERTIES;

    if(i > 0 && Test.PROPERTIES.contains(property)) {
      System.out.println("foobarbaz");
    }
  }
}
