import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.ParameterizedTest;

class MalformedParameterizedCollection {
  @ParameterizedTest
  @EmptySource
  void testFooSet(Set<String> input) {}

  @ParameterizedTest
  @EmptySource
  void testFooList(List<String> input) {}

  @ParameterizedTest
  @EmptySource
  void testFooMap(Map<String, String> input) {}
}
