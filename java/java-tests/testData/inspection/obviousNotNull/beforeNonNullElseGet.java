// "Remove redundant null-check" "true"
import java.util.*;

public class Test {
  public void test() {
    Integer value = Objects.requireNonNullElseGet(5<caret>5, () -> 66);
  }
}