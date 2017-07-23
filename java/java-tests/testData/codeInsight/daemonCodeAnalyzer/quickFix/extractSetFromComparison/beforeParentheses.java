// "Extract Set from comparison chain" "true"
public class Test {
  public static final String BAR = "bar";

  void testOr(int i, String property) {
    int PROPERTIES;

    if(i > 0 && ("foo"<caret>.equals(property) || BAR.equals(property) || "baz".equals(property))) {
      System.out.println("foobarbaz");
    }
  }
}
