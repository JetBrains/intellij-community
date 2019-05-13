// "Extract Set from comparison chain" "true"

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class Test {
    private static final Set<Status> STATUSES = Collections.unmodifiableSet(EnumSet.of(Status.VALID, Status.PENDING));

    enum Status {
    VALID, PENDING, INVALID, UNKNOWN;
  }

  void test1(Status status) {
    if(STATUSES.contains(status)) {
      System.out.println("ok");
    }
  }

  static class Another {
    static final String STATUSES = "";

    void test2(Status st) {
      if(st == null || Test.STATUSES.contains(st) || Math.random() > 0.5) {
        System.out.println("Replace here as well");
      }
    }

    void test3(Status st2) {
      if(st2 == Status.VALID || st2 == Status.PENDING || st2 == Status.UNKNOWN) {
        System.out.println("Do not replace as we test three statuses");
      }
    }
  }
}
