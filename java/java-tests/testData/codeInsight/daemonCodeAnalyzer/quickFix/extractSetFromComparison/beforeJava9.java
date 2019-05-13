// "Extract Set from comparison chain" "true"
public class Test {
  void testOr(int i, String property) {
    if(i > 0 || "foo"<caret>.equals(property) || "bar".equals(property) || "baz".equals(property) || i == -10) {
      System.out.println("foobarbaz");
    }
  }
}
