// "Fuse HashSet into the Stream API chain" "true-preview"
import java.util.*;
import java.util.stream.*;

class X {
  void foo(Stream<String> s) {
    Set<String> set = new HashSet<>(s.co<caret>llect(Collectors.toSet()));
  }
}