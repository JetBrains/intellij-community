// "Apply conversion '.toArray(new java.lang.String[0])'" "true-preview"
import java.util.*;

public class Demo {
  void test2(Collection<String> collection) {
    Set<String[]> integers = Collections.singleton(collection.toArray(new String[0]));
  }
}