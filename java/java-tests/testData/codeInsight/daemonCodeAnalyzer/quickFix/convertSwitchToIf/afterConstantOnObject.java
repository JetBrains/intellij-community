import java.util.Objects;

// "Replace 'switch' with 'if'" "true-preview"
class Test {
  void test(Object obj) {
      if (Objects.requireNonNull(obj) == 1) {
      }
  }
}