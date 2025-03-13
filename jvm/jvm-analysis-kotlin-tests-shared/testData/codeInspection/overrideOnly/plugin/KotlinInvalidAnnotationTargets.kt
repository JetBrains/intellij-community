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

@OverrideOnly
open class SomeOpenClass {
  @OverrideOnly
  open fun <warning descr="Annotation '@ApiStatus.OverrideOnly' is redundant">foo</warning>() {}
}

open class OpenClass {
  @OverrideOnly
  open fun overridableFun() {}

  @OverrideOnly
  fun <warning descr="Method 'notOverridableFun()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">notOverridableFun</warning>() {}

  @OverrideOnly
  private fun <warning descr="Method 'foo()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">foo</warning>() {}

  companion object {
    @OverrideOnly
    private fun <warning descr="Method 'blah()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">blah</warning>() {}
  }
}

@OverrideOnly
open class NonFinalClass {
  fun blah() {}
}

@OverrideOnly
class <warning descr="Class 'FinalClass' is marked with '@ApiStatus.OverrideOnly', but it cannot be extended, nor its methods overridden">FinalClass</warning> {
  fun blah() {}
}

@OverrideOnly
enum class <warning descr="Class 'SomeEnum' is marked with '@ApiStatus.OverrideOnly', but it cannot be extended, nor its methods overridden">SomeEnum</warning> { BLACK, WHITE }
