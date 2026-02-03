// "Replace lambda with method reference" "true-preview"
import java.util.function.Function;

class Bar extends Random {
  public void test(Object obj) {
    Function<Object, int[]> fn = s -> (int[])<caret>s;
  }
}