public class LoopReassignment {

  public void test() {
    String replacement = "foo";
    String <caret>original = replacement;

    for (int i = 0; i < 10; i++) {
      System.out.println(original);
      replacement = "bar";
    }
  }

}