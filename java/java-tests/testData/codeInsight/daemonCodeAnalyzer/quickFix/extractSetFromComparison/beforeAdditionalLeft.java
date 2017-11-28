// "Extract Set from comparison chain" "true"
public class Test {
  void testOr(String name) {
    if(name == null || <caret>"foo".equals(name) || "bar".equals(name) || "baz".equals(name)) {
      System.out.println("foobarbaz");
    }
  }
}
