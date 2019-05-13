// "Replace with 'Set.of' call" "false"
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Test {
  static final String CONST = "b";

  public static final Set<String> MY_SET = Set.of("a", "b", "c", CONST);
}
