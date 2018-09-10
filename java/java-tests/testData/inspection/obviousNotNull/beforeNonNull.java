// "Remove redundant null-check" "true"
import java.util.*;

public class Test {
  public void test() {
    Objects.requireNonNull(5<caret>5);
  }
}