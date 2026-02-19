// "Replace lambda with method reference" "false"
import java.util.function.Function;

class Bar extends Random {
  public void test(Object obj) {
    Function<Object, Object> fn = s -> (Cloneable & Serializable)<caret>s;
  }
}