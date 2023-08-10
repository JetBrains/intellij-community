import org.checkerframework.checker.tainting.qual.Untainted

class CallsCheck {
  fun testCall(dirty: String, clean: @Untainted String) {
    sink(<warning descr="Unknown string is used as safe parameter">dirty</warning>) //warn
    sink("")
    sink(cleanMethod())
    sink(<warning descr="Unknown string is used as safe parameter">publicMethod()</warning>) //warn
    sink(publicFinalMethod())
    sink(<warning descr="Unknown string is used as safe parameter">privateDirty(dirty)</warning>) //warn
    sink(clean)
  }

  private fun privateDirty(dirty: String): String {
    return dirty
  }

  <warning descr="[NON_FINAL_MEMBER_IN_FINAL_CLASS] 'open' has no effect in a final class">open</warning> fun publicMethod(): String {
    return "1"
  }

  fun publicFinalMethod(): String {
    return "1"
  }

  private fun cleanMethod(): String {
    return "null"
  }

  fun sink(<warning descr="[UNUSED_PARAMETER] Parameter 'clean' is never used">clean</warning>: @Untainted String?) {}
}
