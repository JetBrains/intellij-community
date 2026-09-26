public class OuterLoopReassignment {

  public void test() {
    String replacement = "foo";
    String <caret>original = replacement;

    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        System.out.println(original);
      }
      replacement = "bar";
    }
  }

}