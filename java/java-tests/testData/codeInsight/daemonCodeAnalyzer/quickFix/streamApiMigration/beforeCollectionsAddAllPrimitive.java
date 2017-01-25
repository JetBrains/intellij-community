// "Replace with toArray" "true"
import java.util.*;

public class Test {
  String[] test(int count) {
    List<String> result = new ArrayList<>();
    for(int <caret>i=0; i < count; i++) {
      Collections.addAll(result, "one", "two", "three");
    }
    return result.toArray(new String[result.size()]);
  }
}
