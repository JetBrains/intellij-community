import org.checkerframework.checker.tainting.qual.Untainted

class DropLocalityKt {

  class Local(var t: String)

  fun test(s1: @Untainted Local) {

    var s2 = <warning descr="[VARIABLE_WITH_REDUNDANT_INITIALIZER] Variable 's2' initializer is redundant">Local("")</warning>
    sink(s1.t)
    s2 = s1
    sink(s2.t)

    sink(<warning descr="Unknown string is used as safe parameter">if (true) {
      update(s2)
      s2.t
    } else {
      ""
    }</warning>) //warn
  }

  private fun update(s1: Local) {
    s1.t = ""
  }


  fun sink(<warning descr="[UNUSED_PARAMETER] Parameter 'string' is never used">string</warning>: @Untainted String?) {}

}