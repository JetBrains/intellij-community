// "Insert '.apply' to call functional interface method" "true"
import java.util.function.Function;

public class Test {
  public void test(Function<String, String> fn) {
    String res = f<caret>n("foo");
  }
}