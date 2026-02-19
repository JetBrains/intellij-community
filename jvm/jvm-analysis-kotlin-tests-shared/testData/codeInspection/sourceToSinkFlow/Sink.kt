@file:Suppress("unused", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE")

import org.checkerframework.checker.tainting.qual.Tainted
import org.checkerframework.checker.tainting.qual.Untainted

val dirty: @Tainted String = ""

var clean: @Untainted String = <warning descr="Unsafe string is used in a safe context">dirty</warning> //warn

var clean2: @Untainted String = ""

class SinkTestKotlin {

  fun breakClean2(dirty: String) {
    clean2 = <warning descr="Unknown string is returned from safe method">dirty</warning> // warn
  }

  companion object {
    val dirty: @Tainted String = ""

    var clean: @Untainted String = <warning descr="Unsafe string is used in a safe context">dirty</warning> //warn

    var clean2: @Untainted String = ""

    fun breakClean2(dirty: String) {
      clean2 = <warning descr="Unknown string is returned from safe method">dirty</warning> // warn
    }
  }

  fun test(string: String?) {
    sink(<warning descr="Unknown string is used as safe parameter">string</warning>) //warn
  }

  fun returnDirty(dirty: String?): @Untainted String? {
    return <warning descr="Unknown string is returned from safe method">dirty</warning> //warn
  }

  fun sink(clear: @Untainted String?) {
  }

  fun assignDirty(clear: @Untainted String?, dirty: String?) {
    var clear1 = clear
    var clear2: String? = clear1
    clear1 = <warning descr="Unknown string is assigned to safe variable">dirty</warning> //warn
    clear2 = dirty
  }

  var dirty: @Untainted String? = <warning descr="Unsafe string is used in a safe context">getFromStatic()</warning> //warn

  private fun getFromStatic(): @Tainted String {
    return ""
  }

  var clear: @Untainted String? = ""

  fun spoil(dirty: String?) {
    clear = <warning descr="Unknown string is returned from safe method">dirty</warning> //warn
  }

  fun testLocal(dirty: String?) {
    val clean: @Untainted String? = <warning descr="Unknown string is assigned to safe variable">dirty</warning>  //warn
  }

  fun testParameter(clean: @Untainted String = <warning descr="Unsafe string is used as safe parameter">getDirty()</warning>) { //warn

  }

  fun getDirty(): @Tainted String = ""

  fun testLocal2(dirty: String?) {
    var clean: @Untainted String? = ""
    clean = <warning descr="Unknown string is assigned to safe variable">dirty</warning> //warn
  }

  fun println(t: String): String {
    return t
  }
}