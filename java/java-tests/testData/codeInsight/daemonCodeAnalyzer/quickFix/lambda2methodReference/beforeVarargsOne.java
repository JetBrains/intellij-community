// "Replace lambda with method reference" "true-preview"
import java.util.function.BiFunction;

class Example {
  public static void main(String[] args) {
    BiFunction<String, String, Example> f3 = (a, b) -> new<caret> Example(a, b);
  }
  public Example(String a, String... b) {}
}