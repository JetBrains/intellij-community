// "Create local variable 'set'" "true-preview"
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class X {
  void test() {
      Set<Integer> set = Stream.of(1, 2, 3).collect(Collectors.toSet());
  }
}