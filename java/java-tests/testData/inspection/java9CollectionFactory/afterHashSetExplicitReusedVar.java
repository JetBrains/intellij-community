// "Replace with 'Set.of' call" "true"
import java.util.*;

public class Test {
  public void test2() {
    Set<String> set;
      set = Set.of("foo", "bar", "xyz");
    System.out.println(set);
  }
}