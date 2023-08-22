class FromMethod {
  fun test(untidy: String, unknown: String) {
    sink("", <warning descr="Unsafe string is used as safe parameter">untidy</warning>)
    sink("", <warning descr="Unknown string is used as safe parameter">unknown</warning>)
    sink("", <warning descr="Unsafe string is used as safe parameter">unknown.toString()</warning>)
  }

  private fun sink(<warning descr="[UNUSED_PARAMETER] Parameter 'any' is never used">any</warning>: String, <warning descr="[UNUSED_PARAMETER] Parameter 'clean' is never used">clean</warning>: String) {}
}
