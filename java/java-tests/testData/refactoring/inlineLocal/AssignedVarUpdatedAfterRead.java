public class AssignedVarUpdatedAfterRead {

  public void test() {
    String replacement = "foo";

    String <caret>original = replacement;
    System.out.println(original);

    replacement = "bar";

    original = replacement;
    System.out.println(original);
  }

}