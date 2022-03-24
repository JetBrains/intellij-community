import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Test {
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.MethodSource
  @SuppressWarnings("unused")
  void testMethod(String argument) {
    assertNotNull(argument);
  }
  static Stream<String> testMethod() {
    return Stream.of("foo", "bar");
  }
}