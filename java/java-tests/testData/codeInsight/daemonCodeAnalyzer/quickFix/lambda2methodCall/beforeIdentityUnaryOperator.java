// "Replace lambda expression with 'Function.identity()'" "false"
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public class Main {
  private final Pattern pattern = Pattern.compile("[a-z]");

  public <T extends CharSequence> void test() {
    Object str = "def";
    String xyz = Optional.of("xyz").map(x -> x).filter(x -> Objects.equals(x, str))
      .filter(str -> pattern.matcher(str).find()).orElse("");
    UnaryOperator<T> op = x <caret>-> x;
  }
}
