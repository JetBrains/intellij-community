// "Replace Stream API chain with loop" "true-preview"

import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TemplateTest {
  void test() {
    System.out.println(STR."\{Stream.of(1,2,3).<caret>collect(Collectors.toList())}");
  }
}