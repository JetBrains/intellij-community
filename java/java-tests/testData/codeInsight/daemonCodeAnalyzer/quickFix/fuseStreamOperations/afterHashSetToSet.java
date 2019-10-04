// "Fuse HashSet into the Stream API chain" "true"
import java.util.*;
import java.util.stream.*;

class X {
  void foo(Stream<String> s) {
    Set<String> set = s.collect(Collectors.toSet());
  }
}