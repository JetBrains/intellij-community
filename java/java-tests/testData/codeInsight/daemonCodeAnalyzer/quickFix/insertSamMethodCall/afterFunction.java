// "Insert '.apply' to call functional interface method" "true-preview"
import java.util.function.Function;

public class Test {
  public void test(Function<String, String> fn) {
    String res = fn.apply("foo");
  }
}