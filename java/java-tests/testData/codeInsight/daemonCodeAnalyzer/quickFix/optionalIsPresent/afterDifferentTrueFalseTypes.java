// "Replace Optional presence condition with functional style expression" "true-preview"

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class TestFile {

  public static Collection<String> example() {
    final Optional<String> root = Optional.empty();
      return root.<Collection<String>>map(TestFile::foo).orElse(Collections.emptyList());
  }

  private static Set<String> foo(String s) {
    return Collections.emptySet();
  }
}