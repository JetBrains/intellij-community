import java.util.Set;

// "Extract Set from comparison chain" "true"
public class Test {
    private static final Set<String> PROPERTIES = Set.of("foo", "bar", "baz");

    void testOr(int i, String property) {
    if(i > 0 || PROPERTIES.contains(property) || i == -10) {
      System.out.println("foobarbaz");
    }
  }
}
