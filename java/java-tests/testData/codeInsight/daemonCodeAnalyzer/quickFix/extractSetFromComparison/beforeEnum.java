// "Extract Set from comparison chain" "true"

public class Test {
  enum Status {
    RUNNING, PENDING, DONE, STARTED;
  }

  void testEq(Status status1, Status status) {
    if(status1 == Status.RUNNING || status1 == Status.PENDING || status =<caret>= Status.DONE || Status.STARTED == status) {
      System.out.println("foobarbaz");
    }
  }
}
