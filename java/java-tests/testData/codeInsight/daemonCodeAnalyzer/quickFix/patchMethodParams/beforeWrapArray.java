// "Surround with array initialization" "true-preview"
import java.util.*;

public class Demo {
  void test() {
    Set<int[]> integers = Collections.<caret>singleton(1);
  }
}