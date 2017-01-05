// "Replace lambda expression with 'Function.identity()'" "false"
import java.io.Serializable;
import java.util.function.Function;

public class Main {
  public static void main(String[] args) {
    Function<Object, Object> fn = (Function<Object, Object> & Serializable) x <caret>-> x;
  }
}
