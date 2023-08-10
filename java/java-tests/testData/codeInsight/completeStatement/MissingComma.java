import java.util.Arrays;
import java.util.List;

public class SomeClass {

  public static List<String> someMethod() {
    return Arrays.asList(
      "first",
      "second"<caret>
      "third"
    );
  }
}