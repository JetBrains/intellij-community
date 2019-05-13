// "Replace with 'Set.of' call" "true"
import java.util.*;

public class Test {
  private static final Set<String> MY_SET;

  static {
      MY_SET = Set.of("foo", "bar", "xyz");
  }
}