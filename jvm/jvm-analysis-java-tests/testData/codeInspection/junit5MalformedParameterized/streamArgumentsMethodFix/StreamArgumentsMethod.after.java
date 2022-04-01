import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class Test {
    public static Stream<Arguments> parameters() {
        return null;
    }

    @MethodSource("parameters")
  @ParameterizedTest
  void foo(String param) {}
}