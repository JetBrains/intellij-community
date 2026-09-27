// "Extract Set from comparison chain" "true-preview"

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class NoNameClash {
  // An existing field. The name is already taken.
  private static final Set<String> CLI_SWITCHES = Set.of("--x");
    private static final Set<String> CLI_SWITCHES1 = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("--dlib", "--daemon")));

    boolean test(String cliSwitch) {
    return CLI_SWITCHES1.contains(cliSwitch);
  }
}