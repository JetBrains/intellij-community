public class AssignedVarsUpdatedBeforeRead {

  public void test(String p1, String p2) {
    String <caret>f = foo(p1, p2);

    p2 = "bar";
    p1 = "baz";
    p2 = "foo";

    System.out.println(f);
  }

  public String foo(String p1, String p2) {
    return p1;
  }

}