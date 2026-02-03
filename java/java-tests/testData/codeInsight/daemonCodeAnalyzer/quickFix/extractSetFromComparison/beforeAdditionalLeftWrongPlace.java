// "Extract Set from comparison chain" "false"
public class Test {
  void testOr(String s) {
    if(s == nul<caret>l || "foo".equals(s) || "bar".equals(s) || "baz".equals(s)) {
      System.out.println("foobarbaz");
    }
  }
}
