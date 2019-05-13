// "Replace with 'Set.of' call" "true"
import java.util.*;

public class Test {
  public void test2() {
    Set<String> set = new HashSet<>();
    set.add("foo");
    set.add("bar");
    set.add("xyz");
    set = Collections.unmodif<caret>iableSet(set);
    System.out.println(set);
  }
}