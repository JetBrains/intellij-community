// "Replace lambda expression with 'Function.identity()'" "true"
import java.util.function.Function;

class Scratch {
  public static void main(String[] args) {
    Function<String, String> myFunc = c <caret>-> c;
  }
}