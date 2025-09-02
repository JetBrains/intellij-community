// "Create local variable 'set'" "true-preview"
import java.util.stream.Collectors;
import java.util.stream.Stream;

class X {
  void test() {
    <caret>set = Stream.of(1, 2, 3).collect(Collectors.toSet());
  }
}