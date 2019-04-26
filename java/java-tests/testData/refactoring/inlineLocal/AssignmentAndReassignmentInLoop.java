public class AssignmentAndReassignmentInLoop {

  public void test() {
    for(int i = 0; i < 3; i++) {
      String replacement = "foo";
      String <caret>original = replacement;

      replacement = "bar";

      System.out.println(original);
    }
  }

}