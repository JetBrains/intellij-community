import java.util.Objects;
import java.util.function.Predicate;

public class Test {
  public static void main(String[] args) {
    Predicate<String> p = String::isEmpty.not<caret>;
  }
}