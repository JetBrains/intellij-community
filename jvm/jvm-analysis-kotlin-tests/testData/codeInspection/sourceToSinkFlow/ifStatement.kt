import org.checkerframework.checker.tainting.qual.Untainted

internal class IfStatement {
  fun test1(a: String?) {
    sink(<warning descr="Unknown string is used as safe parameter">a</warning>) //warn
  }

  fun test2(a: String?) {
    var <warning descr="[NAME_SHADOWING] Name shadowed: a">a</warning> = <warning descr="[VARIABLE_WITH_REDUNDANT_INITIALIZER] Variable 'a' initializer is redundant">a</warning>
    a = "2"
    sink(a) //no
  }

  fun test3(a: String) {
    var <warning descr="[NAME_SHADOWING] Name shadowed: a">a</warning> = a
    if (a.length == 1) {
      a = "3"
    }
    sink(<warning descr="Unknown string is used as safe parameter">a</warning>) //warn
  }

  fun test4(a: String) {
    var <warning descr="[NAME_SHADOWING] Name shadowed: a">a</warning> = a
    a = if (a.length == 1) {
      "3"
    } else {
      a
    }
    sink(<warning descr="Unknown string is used as safe parameter">a</warning>) //warn
  }

  fun test5(a: String) {
    var <warning descr="[NAME_SHADOWING] Name shadowed: a">a</warning> = a
    a = if (a.length == 1) {
      "3"
    } else {
      "a"
    }
    sink(a) //no
  }

  companion object {
    fun sink(<warning descr="[UNUSED_PARAMETER] Parameter 't' is never used">t</warning>: @Untainted String?) {}
  }
}
