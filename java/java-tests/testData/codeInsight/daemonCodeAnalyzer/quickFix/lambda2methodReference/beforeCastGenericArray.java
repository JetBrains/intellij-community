// "Replace lambda with method reference" "false"
import java.util.function.Function;

class Bar extends Random {
  public void <T> test(Object obj) {
    Function<Object, T[]> fn = s -> (T[])<caret>s;
  }
}