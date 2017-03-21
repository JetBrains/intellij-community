// "Replace with 'Set.of' call" "true"
import java.util.*;

public class Test {
  private static final Set<String> MY_SET;

  static {
    Set<String> set = new HashSet<>();
    set.add("foo");
    set.add("bar");
    set.add("xyz");
    MY_SET = Collections.unmodif<caret>iableSet(set);
  }
}