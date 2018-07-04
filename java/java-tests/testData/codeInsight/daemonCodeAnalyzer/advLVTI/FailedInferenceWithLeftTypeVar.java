import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class MyTest {
  private void getMethods(final Stream<Method> stream) {
    final var collector = Collectors.toMap(null, c -> {
      System.out.println(c);
      return new LinkedHashMap<String, Integer>();
    }, (v1, v2) -> v2, null);
  }
}