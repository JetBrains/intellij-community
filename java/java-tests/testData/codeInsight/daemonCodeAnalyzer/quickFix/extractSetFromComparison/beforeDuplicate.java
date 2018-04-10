// "Extract Set from comparison chain" "true"

public class Test {
  enum Status {
    VALID, PENDING, INVALID, UNKNOWN;
  }

  void test1(Status status) {
    if(status =<caret>= Status.VALID || status == Status.PENDING) {
      System.out.println("ok");
    }
  }

  static class Another {
    static final String STATUSES = "";

    void test2(Status st) {
      if(st == null || Status.PENDING == st || Status.VALID == st || Math.random() > 0.5) {
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
