import org.checkerframework.checker.tainting.qual.Untainted
import java.util.*

internal class LambdaWithForEachLoop {
  fun lambdaIterate() {
    val queries: List<String> =
      Arrays.asList("select c from sample s", "select c.sample from Sample c where c.color = 'red")
    queries.forEach { query: String? ->
      sink(
          query!!
      )
    }
  }

  fun lambdaIterateDirty(untidy: String) {
    val queries =
      Arrays.asList("select c from sample s", "select c.sample from Sample c where c.color = 'red", untidy)
    queries.forEach { query: String? ->
      sink(
          <warning descr="Unknown string is used as safe parameter">query!!</warning>
      )
    }
  }

  fun sink(<warning descr="[UNUSED_PARAMETER] Parameter 'clean' is never used">clean</warning> : @Untainted String?) {}
}
