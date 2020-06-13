// "Replace lambda with method reference" "true"
import java.util.function.BiFunction;

class Example {
  public static void main(String[] args) {
    BiFunction<String, String, Example> f3 = Example::new;
  }
  public Example(String a, String... b) {}
}