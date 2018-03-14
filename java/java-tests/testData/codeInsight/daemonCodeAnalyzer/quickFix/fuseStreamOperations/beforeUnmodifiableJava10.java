// "Fix all 'Subsequent steps can be fused into Stream API chain' problems in file" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  List<String> testFuse(String[] list) {
    List<String> result = Arrays.stream(list).filter(s -> !s.isEmpty()).co<caret>llect(Collectors.toCollection(LinkedList::new));
    return Collections.unmodifiableList(result);
  }

  Set<String> testFuse2(String[] list) {
    return Collections.unmodifiableSet(Arrays.stream(list).filter(s -> !s.isEmpty()).collect(Collectors.toSet()));
  }
}