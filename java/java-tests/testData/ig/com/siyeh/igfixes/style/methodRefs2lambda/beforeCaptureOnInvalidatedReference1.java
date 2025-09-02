// "Replace method reference with lambda" "true-preview"
import java.util.Optional;

class MyTest {
  private static Class<?> test(Optional<Object> value) {
    return value.map(Object::get<caret>Class).orElse(int.class);
  }
}