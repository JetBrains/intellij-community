import org.checkerframework.checker.tainting.qual.Untainted

open class MethodPropagation {
  fun test1(dirty: String?, clean: @Untainted String?) {

    sink(<warning descr="Unknown string is used as safe parameter">dirty</warning>) //warn
    sink(clean)
  }

  private fun recursive(dirty: String?, clean: @Untainted String?): String {
    if (clean === "") {
      val a = recursive(dirty, clean)
      sink(<weak_warning descr="Too complex to check that the string is safe in a safe context">a</weak_warning>) //warn
      return recursive(dirty, clean)
    }
    return recursive(clean, clean)
  }

  fun test2(dirty: String?, clean: @Untainted String?) {
    sink(<warning descr="Unknown string is used as safe parameter">next(dirty)</warning>) //warn
    sink(next(clean))
    sink(<warning descr="Unknown string is used as safe parameter">nextPublic(dirty)</warning>) //warn
    sink(<warning descr="Unknown string is used as safe parameter">nextPublic(clean)</warning>) //warn (public)
    sink(alwaysClean(dirty))
    sink(alwaysClean(clean))
    sink(<warning descr="Unknown string is used as safe parameter">staticNext(dirty)</warning>) //warn
    sink(staticNext(clean))
    sink(next(next(clean)))
    sink(next(next(next(next(next(clean))))))
    sink(<warning descr="Unknown string is used as safe parameter">next(next(next(next(dirty))))</warning>) //warn
    sink(alwaysClean(next(next(next(next(clean))))))
    sink(alwaysClean(next(next(next(next(dirty))))))
    sink(next(next(next(next(alwaysClean(clean))))))
    sink(next(next(next(next(alwaysClean(dirty))))))
    sink(next(alwaysClean(clean)))
    sink(next(alwaysClean(dirty)))
    val alwaysClean = alwaysClean(next(next(next(clean))))
    sink(alwaysClean)
    val alwaysClean2 = alwaysClean(next(next(next(dirty))))
    sink(alwaysClean2)
  }

  private fun next(next: String?): String? {
    return next
  }

  private fun alwaysClean(<warning descr="[UNUSED_PARAMETER] Parameter 'next' is never used">next</warning>: String?): String {
    return "next"
  }

  open fun nextPublic(next: String?): String? {
    return next
  }

  companion object {
    fun staticNext(next: String?): String? {
      return next
    }

    fun sink(<warning descr="[UNUSED_PARAMETER] Parameter 'string' is never used">string</warning>: @Untainted String?) {}
  }
}
