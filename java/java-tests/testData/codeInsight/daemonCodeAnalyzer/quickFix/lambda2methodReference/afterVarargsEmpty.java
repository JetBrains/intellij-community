// "Replace lambda with method reference" "true-preview"
import java.util.function.Function;

class Example {
  public static void main(String[] args) {
    Function<String, Example> f1 = Example::new;
  }
  public Example(String a, String... b) {}
}