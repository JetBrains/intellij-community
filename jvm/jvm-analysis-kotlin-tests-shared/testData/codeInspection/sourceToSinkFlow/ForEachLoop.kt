import org.checkerframework.checker.tainting.qual.Untainted
import java.util.*

internal class ForEachLoop {
  fun testLoopClean() {
    val queries: List<String> = Arrays.asList("select s from Sample s", "select s from Sample s where s.color = 'red'")
    for (query in queries) {
      sink(query)
    }
  }

  fun testLoopDirty(dirty: String) {
    val queries = Arrays.asList("select s from Sample s", "select s from Sample s where s.color = 'red'", dirty)
    for (query in queries) {
      sink(<warning descr="Unknown string is used as safe parameter">query</warning>)
    }
  }
  fun sink(<warning descr="[UNUSED_PARAMETER] Parameter 'clean' is never used">clean</warning> : @Untainted String?) {}
}
