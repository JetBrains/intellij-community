// "Replace with a null check" "true-preview"
import java.util.stream.Stream;

class Test {
  void test(Stream<String> s) {
    s.filter(String.class::<caret>isInstance)
      .forEach(System.out::println);
  }
}