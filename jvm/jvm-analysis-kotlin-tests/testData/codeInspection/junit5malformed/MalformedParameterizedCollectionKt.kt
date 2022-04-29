import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EmptySource

object MalformedParameterizedCollectionKt {
  @ParameterizedTest
  @EmptySource
  fun testFooSet(input: Set<String?>?) {}

  @ParameterizedTest
  @EmptySource
  fun testFooList(input: List<String?>?) {}

  @ParameterizedTest
  @EmptySource
  fun testFooMap(input: Map<String?, String?>?) {}
}
