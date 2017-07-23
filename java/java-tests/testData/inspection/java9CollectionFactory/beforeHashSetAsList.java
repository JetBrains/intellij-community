// "Replace with 'Set.of' call" "true"
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Test {
  public static final Set<String> MY_SET = Collections.unm<caret>odifiableSet(new HashSet<>(Arrays.asList("a", "b", "c", Math.random() > 0.5 ? "d" : Math.random() > 0.5 ? "e" : "d")));
}
