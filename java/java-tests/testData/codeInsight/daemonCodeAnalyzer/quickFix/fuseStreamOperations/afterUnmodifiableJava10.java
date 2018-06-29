// "Fix all 'Subsequent steps can be fused into Stream API chain' problems in file" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  List<String> testFuse(String[] list) {
      return Arrays.stream(list).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableList());
  }

  Set<String> testFuse2(String[] list) {
    return Arrays.stream(list).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
  }
}