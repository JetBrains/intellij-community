// "Replace lambda with method reference" "false"
import java.util.function.Function;

class Bar extends Random {
  public void test(Object obj) {
    Function<Object, List<String>> fn = s -> (List<String>)<caret>s;
  }
}