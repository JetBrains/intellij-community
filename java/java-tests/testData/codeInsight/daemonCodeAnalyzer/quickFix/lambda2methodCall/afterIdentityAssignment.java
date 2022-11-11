// "Replace lambda expression with 'Function.identity()'" "true-preview"
import java.util.function.Function;

class Scratch {
  public static void main(String[] args) {
    Function<String, String> myFunc = Function.identity();
  }
}