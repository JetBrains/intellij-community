// "Extract Set from comparison chain" "true-preview"
public interface Test {
  default void testOr(String s) {
    if("foo"<caret>.equals(s) || "bar".equals(s) || "baz".equals(s)) {
      System.out.println("foobarbaz");
    }
  }
}
