// "Remove redundant null-check" "true"
import java.util.*;

public class Test {
  public void test() {
    System.out.println(Objects.requireNonNull(5<caret>5).hashCode());
  }
}