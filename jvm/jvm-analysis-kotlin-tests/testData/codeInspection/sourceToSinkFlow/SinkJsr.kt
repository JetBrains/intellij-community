@file:Suppress("unused", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE")

import javax.annotation.Tainted
import javax.annotation.Untainted


@Tainted
val dirty: String = ""

@Untainted
var clean: String = <warning descr="Unsafe string is used in a safe context">dirty</warning> //warn

@Untainted
var clean2: String = ""

class SinkTestKotlin {

  fun breakClean2(dirty: String) {
    clean2 = <warning descr="Unknown string is returned from safe method">dirty</warning> // warn
  }

  companion object {
    @Tainted
    val dirty: String = ""

    @Untainted
    var clean: String = <warning descr="Unsafe string is used in a safe context">dirty</warning> //warn

    @Untainted
    var clean2: String = ""

    fun breakClean2(dirty: String) {
      clean2 = <warning descr="Unknown string is returned from safe method">dirty</warning> // warn
    }
  }

  fun test(string: String?) {
    sink(<warning descr="Unknown string is used as safe parameter">string</warning>) //warn
  }

  @Untainted
  fun returnDirty(dirty: String?): String? {
    return <warning descr="Unknown string is returned from safe method">dirty</warning> //warn
  }


  fun sink(@Untainted clear: String?) {
    println(clear!!)
  }

  fun assignDirty(@Untainted clear: String?, dirty: String?) {
    @Untainted var clear1: String? = clear
    var clear2: String? = clear1
    clear1 = <warning descr="Unknown string is assigned to safe variable">dirty</warning> //warn
    clear2 = dirty
  }

  @Untainted
  var dirty: String? = <warning descr="Unsafe string is used in a safe context">getFromStatic()</warning> //warn

  @Tainted
  private fun getFromStatic(): String {
    return ""
  }

  @Untainted
  var clear: String? = ""

  fun spoil(dirty: String?) {
    clear = <warning descr="Unknown string is returned from safe method">dirty</warning> //warn
  }

  fun testLocal(dirty: String?) {
    @Untainted val clean: String? = <warning descr="Unknown string is assigned to safe variable">dirty</warning>  //warn
  }

  fun testParameter(@Untainted clean: String = <warning descr="Unsafe string is used as safe parameter">getDirty()</warning>) { //warn

  }

  @Tainted
  fun getDirty(): String = ""

  fun testLocal2(dirty: String?) {
    @Untainted var clean: String? = ""
    clean = <warning descr="Unknown string is assigned to safe variable">dirty</warning> //warn
  }

  fun println(t: String): String {
    return t
  }
}