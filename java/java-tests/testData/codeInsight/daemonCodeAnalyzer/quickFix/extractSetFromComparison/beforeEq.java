// "Extract Set from comparison chain" "true"

public class Test {
  enum Status {
    RUNNING, PENDING, DONE, STARTED;
  }

  void testEq(String name, Status status) {
    if(name =<caret>= "foo" || name == "bar" || "baz" == name || status == Status.DONE || status == Status.PENDING) {
      System.out.println("foobarbaz");
    }
  }
}
