@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")

import org.checkerframework.checker.tainting.qual.Untainted

class LocalCheck {
  fun test(clean: @Untainted MutableList<String?>, cleanList2: @Untainted MutableList<String>, t: @Untainted String?, dirty: String?) {
    sink(t)
    sink(clean[0])
    val list1: List<String?> = clean
    update(list1) //not highlighted in current realisation, might be changed
    clean.add(dirty) //not highlighted in current realisation, might be changed
    sink(<warning descr="Unknown string is used as safe parameter">list1[0]</warning>) //warn
    sink(clean[0]) //warn
    val list3: List<String> = cleanList2
    sink(list3[0])
    sink(<warning descr="Unknown string is used as safe parameter">dirty</warning>) //warn
    var clean2 = t + dirty
    sink(<warning descr="Unknown string is used as safe parameter">clean2</warning>) // warn
    var newT: String?
    newT = t
    sink(newT)
    val runnable = Runnable {
      sink(<warning descr="Unknown string is used as safe parameter">newT</warning>) //warn
    }
    val runnable2: () -> Unit = { sink(<warning descr="Unknown string is used as safe parameter">newT</warning>) } //warn
    newT = dirty
  }

  private fun update(list: List<String?>?) {}
  fun sink(clean: @Untainted String?) {}
}
