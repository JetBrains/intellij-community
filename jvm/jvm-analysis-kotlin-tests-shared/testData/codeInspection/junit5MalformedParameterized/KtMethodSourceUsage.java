import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class KtMethodSourceUsage {
  @ParameterizedTest
  @MethodSource("SampleTest#squares")
  void testSquares(int input, int expected) {}
}
