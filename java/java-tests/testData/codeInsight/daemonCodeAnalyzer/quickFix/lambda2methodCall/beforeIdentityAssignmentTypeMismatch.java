// "Replace lambda expression with 'Function.identity()'" "false"
import java.util.function.Function;

class Scratch {
  public static void main(String[] args) {
    Function<String, Object> myFunc = c <caret>-> c;
  }
}