@file:Suppress("UNUSED_PARAMETER")

import org.checkerframework.checker.tainting.qual.Untainted

internal class LocalCheck {
  enum class State {
    OFF,
    ON
  }

  annotation class InterfaceSomething

  fun test(clean: @Untainted String?, dirty: String?, state: State, interfaceSomething: InterfaceSomething) {
    sink(clean)
    sink(<warning descr="Unknown string is used as safe parameter">dirty</warning>) //warn
    sink(state.name)
    sink(interfaceSomething.toString())
  }

  fun sink(clean: @Untainted String?) {}
}
