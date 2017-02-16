// "Replace with 'Set.of' call" "false"
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Test {
  static Set<String> WRONG_SET = new HashSet<>();

  public static final Set<String> MY_SET = Collections.unmod<caret>ifiableSet(new HashSet<String>() {{
    add("a");
    add("b");
    WRONG_SET.add("c".toUpperCase());
  }});
}
