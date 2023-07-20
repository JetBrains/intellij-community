import org.checkerframework.checker.tainting.qual.Untainted

class KotlinArguments {
  fun test(dirty: String) {
    sink(<warning descr="Unknown string is used as safe parameter">getFrom(second = dirty, first = "")</warning>)
    sink(getFrom(first = dirty, second = ""))
  }

  private fun getFrom(<warning descr="[UNUSED_PARAMETER] Parameter 'first' is never used">first</warning>: String, second: String): String {
    return second
  }

  fun sink(<warning descr="[UNUSED_PARAMETER] Parameter 's' is never used">s</warning>: @Untainted String?) {}
}
