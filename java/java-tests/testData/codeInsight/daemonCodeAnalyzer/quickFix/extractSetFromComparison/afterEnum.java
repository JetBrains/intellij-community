// "Extract Set from comparison chain" "true"

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class Test {
    private static final Set<Status> STATUSES = Collections.unmodifiableSet(EnumSet.of(Status.DONE, Status.STARTED));

    enum Status {
    RUNNING, PENDING, DONE, STARTED;
  }

  void testEq(Status status1, Status status) {
    if(status1 == Status.RUNNING || status1 == Status.PENDING || STATUSES.contains(status)) {
      System.out.println("foobarbaz");
    }
  }
}
