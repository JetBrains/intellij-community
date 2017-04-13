// "Replace Optional.isPresent() condition with functional style expression" "true"

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class TestFile {

  public static Collection<String> example() {
    final Optional<String> root = Optional.empty();
    if (root<caret>.isPresent()) {
      return foo(root.get());
    }
    return Collections.emptyList();
  }

  private static Set<String> foo(String s) {
    return Collections.emptySet();
  }
}