// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  Stream<String> names() {
    return Stream.of("foo", "bar");
  }

  public static void main(String[] args) {
    List<String> list = new Test().names().map(String::trim).filter(n -> !n.isEmpty())
      .<caret>collect(Collectors.toList());
  }
}