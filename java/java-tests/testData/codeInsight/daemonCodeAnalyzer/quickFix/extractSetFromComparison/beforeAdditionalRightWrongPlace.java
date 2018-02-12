// "Extract Set from comparison chain" "false"
public class Test {
  void testOr(String s) {
    if("foo".equals(s) || "bar".equals(s) || "baz".equals(s) || s<caret> == null) {
      System.out.println("foobarbaz");
    }
  }
}
