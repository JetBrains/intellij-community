// "Replace with 'Set.of' call" "true"
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Test {
  public static final Set<String> MY_SET = Set.<String>of("a", "b", "c".toUpperCase());
}
