// "Replace method reference with lambda" "true-preview"
import java.util.List;
import java.util.stream.Stream;

class MyTest {
  private static Stream<?> apply(MyTest h) { return null; }

  void f(List<MyTest> list) {
    list.stream()
      .flatMap(MyTest::<caret>apply);
  }
}