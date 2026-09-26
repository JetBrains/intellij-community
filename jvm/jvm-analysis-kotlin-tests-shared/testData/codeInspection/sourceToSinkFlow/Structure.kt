@file:Suppress("unused", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE")

import org.checkerframework.checker.tainting.qual.Untainted

class StructureTest {
  fun test(clean: @Untainted String, unclean: String) {
    sink(<warning descr="Unknown string is used as safe parameter">unclean</warning>) //warn
    sink(clean)

    sink(<warning descr="Unknown string is used as safe parameter">if (1 == 1) unclean else clean</warning>) //warn
    sink(if (1 == 1) clean else clean)

    val s = if (1 == 1) unclean else clean
    sink(<warning descr="Unknown string is used as safe parameter">s</warning>) //warn

    sink(<warning descr="Unknown string is used as safe parameter">when {
           1 == 1 -> clean
           else -> unclean
         }</warning>)  //warn

    sink(when {
           1 == 1 -> clean
           else -> clean
         })

    val s1: String = when {
      1 == 1 -> clean
      else -> unclean
    }

    sink(<warning descr="Unknown string is used as safe parameter">s1</warning>)  //warn

    var sumDirty: String = clean
    var sumClean = clean
    while (true) {
      sumDirty += unclean
      sumClean += clean
      break
    }
    sink(<warning descr="Unknown string is used as safe parameter">sumDirty</warning>) //warn
    sink(sumClean)
  }

  fun sink(s: @Untainted String?) {}
}