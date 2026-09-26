import org.checkerframework.checker.tainting.qual.Untainted

class DifferentExpression {
  fun test() {
    sink(this.toString())
    val r = Runnable {}
    sink(<warning descr="Unknown string is used as safe parameter">r.toString()</warning>) //warn
    sink(DifferentExpression::class.toString())
    sink("test" + (1 - 1))
    var x = 1
    sink("test" + ++x)
    sink(param2(<error descr="[NO_VALUE_FOR_PARAMETER] No value passed for parameter 't1'">"1", )</error>) //warn
  }

  companion object {
    fun sink(<warning descr="[UNUSED_PARAMETER] Parameter 'string' is never used">string</warning>: @Untainted String?) {}
    fun param2(<warning descr="[UNUSED_PARAMETER] Parameter 't' is never used">t</warning>: String?, t1: String): String {
      return t1
    }
  }
}
