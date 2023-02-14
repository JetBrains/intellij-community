// "Replace lambda with method reference" "true-preview"
import java.util.function.BiFunction;
import java.util.Arrays;
import java.util.List;

class Example {
  public static void main(String[] args) {
    BiFunction<String, String, List<String>> f3 = (a, b) -> Arrays.<caret>asList(a, b);
  }
}