public class UnusedReassignmentInLoop {

  public void test() {
    for(int i = 0; i < 3; i++) {
      String replacement = "foo";
      String <caret>original = replacement;
      System.out.println(original);

      replacement = "bar";
    }
  }
}