import java.util.*;

// IDEA-356148
public class Example {

  public void example() {

    final Map<String, Object> map = Map.of();
    if (<caret>test((String)map.get("whatever"))) {
      System.out.println("??");
    }

    final Object obj = "foo";
    if (test((String)obj)) {
      System.out.println("??");
    }
  }

  private boolean test(final String str) {
    return Objects.isNull(str);
  }
}