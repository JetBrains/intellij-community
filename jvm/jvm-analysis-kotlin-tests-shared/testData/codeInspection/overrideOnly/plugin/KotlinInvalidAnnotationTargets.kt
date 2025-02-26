package plugin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.OverrideOnly

@ApiStatus.OverrideOnly
fun <warning descr="Method 'foo()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">foo</warning>() {}

@OverrideOnly
fun <warning descr="Method 'bar()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">bar</warning>() {}

class NotOpenClass {
  @OverrideOnly
  fun <warning descr="Method 'foo()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">foo</warning>() {}
}

open class OpenClass {
  @OverrideOnly
  private fun <warning descr="Method 'foo()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">foo</warning>() {}

  companion object {
    @OverrideOnly
    private fun <warning descr="Method 'blah()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">blah</warning>() {}
  }
}
