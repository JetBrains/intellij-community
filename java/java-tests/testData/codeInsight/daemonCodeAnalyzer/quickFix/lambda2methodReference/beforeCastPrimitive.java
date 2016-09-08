// "Replace lambda with method reference" "false"
import java.util.function.ToIntFunction;

class Bar extends Random {
  public void test(Object obj) {
    ToIntFunction<Object> fn = s -> (int)<caret>s;
  }
}