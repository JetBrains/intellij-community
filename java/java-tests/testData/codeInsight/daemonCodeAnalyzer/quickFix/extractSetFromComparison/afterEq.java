// "Extract Set from comparison chain" "true"

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Test {
    private static final Set<String> NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("foo", "bar", "baz")));

    enum Status {
    RUNNING, PENDING, DONE, STARTED;
  }

  void testEq(String name, Status status) {
    if(NAMES.contains(name) || status == Status.DONE || status == Status.PENDING) {
      System.out.println("foobarbaz");
    }
  }
}
