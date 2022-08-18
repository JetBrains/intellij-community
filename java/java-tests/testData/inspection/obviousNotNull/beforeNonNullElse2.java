// "Remove redundant null-check" "true"
import java.util.*;

public class Test {
  public void test() {
    Objects.requireNonNullElse(<caret>new Object(), "hello");
  }
}