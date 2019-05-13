// "Replace with 'Set.of' call" "true"
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Test {
  public static final Set<String> MY_SET = Collections.unmodi<caret>fiableSet(new HashSet<String>() {{
    add("a");
    add("b");
    add("c".toUpperCase());
  }});
}
