// "Extract Set from comparison chain" "true-preview"
import java.util.Objects;

public class Test {
  void testOr(String s) {
    if(Objects.equals(s, null) || "foo"<caret>.equals(s) || s.equals("bar") || Objects.equals("baz", s) || Objects.equals(s, "quz")) {
      System.out.println("foobarbaz");
    }
  }
}
