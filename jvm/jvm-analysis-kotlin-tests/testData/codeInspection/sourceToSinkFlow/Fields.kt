
import org.checkerframework.checker.tainting.qual.Untainted

val cleanOuter = "2"
var notCleanOuter = "2"

class FieldsCheck(val property1: String, private val property2: String) {
  val constant = "1"
  private val clean = "1"
  private var notClean = "1"
  private var clean3 = "1"
  private val clean2 = "2"
  fun setNotClean(notClean: String) {
    this.notClean = notClean
  }

  companion object {
    val cleanOuter2 = "2"
    var notCleanOuter2 = "2"
  }

  fun test() {
    sink(constant)
    sink(clean)
    sink(clean2)
    sink(clean3)
    sink(<warning descr="Unknown string is used as safe parameter">notClean</warning>) //warn
    sink(<warning descr="Unknown string is used as safe parameter">property1</warning>) //warn
    sink(<warning descr="Unknown string is used as safe parameter">property2</warning>) //warn
    sink(cleanOuter)
    sink(<warning descr="Unknown string is used as safe parameter">notCleanOuter</warning>) //warn
    sink(cleanOuter2)
    sink(<warning descr="Unknown string is used as safe parameter">notCleanOuter2</warning>) //warn
  }

  fun sink(<warning descr="[UNUSED_PARAMETER] Parameter 's' is never used">s</warning>: @Untainted String?) {}
}
