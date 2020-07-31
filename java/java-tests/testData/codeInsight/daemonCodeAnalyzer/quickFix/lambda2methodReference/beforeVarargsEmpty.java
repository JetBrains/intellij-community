// "Replace lambda with method reference" "true"
import java.util.function.Function;

class Example {
  public static void main(String[] args) {
    Function<String, Example> f1 = a -> new<caret> Example(a);
  }
  public Example(String a, String... b) {}
}