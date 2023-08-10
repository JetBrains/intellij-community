@file:Suppress("UNUSED_PARAMETER")

import org.checkerframework.checker.tainting.qual.Tainted
import org.checkerframework.checker.tainting.qual.Untainted

class LocalCheck {
  fun test(clean: @Untainted String = <warning descr="Unsafe string is used as safe parameter">dirty()</warning>) {
  }

  private fun dirty(): @Tainted String {
    return ""
  }
}
