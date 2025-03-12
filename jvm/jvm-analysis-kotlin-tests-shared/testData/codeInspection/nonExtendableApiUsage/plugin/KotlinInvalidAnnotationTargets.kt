package plugin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.NonExtendable

@ApiStatus.NonExtendable
fun <warning descr="'foo()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">foo</warning>() {}

@NonExtendable
fun <warning descr="'bar()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">bar</warning>() {}

class NotOpenClass {
  @NonExtendable
  fun <warning descr="'foo()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">foo</warning>() {}
}

@NonExtendable
open class SomeOpenClass {
  @NonExtendable
  open fun <warning descr="Annotation '@ApiStatus.NonExtendable' is redundant">someOpenMethod</warning>() {}

  open fun someAnotherOpenMethod() {}
}

open class OpenClass {
  @NonExtendable
  private fun <warning descr="'foo()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">foo</warning>() {}

  @NonExtendable
  fun <warning descr="'bar()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">bar</warning>() {}

  @NonExtendable
  open fun thisCouldBeActuallyExtended() {

    @NonExtendable
    fun nested() {
      // Uncovered edge case: in Kotlin UAST, a nested function is a UVariable, not UMethod.
      // This case is not prevented by @ApiStatus.NonExtendable having target types restricted to: ElementType.TYPE, ElementType.METHOD
      // It seems uncommmon enough that it's not worth handling it.
    }
  }

  companion object {
    @NonExtendable
    private fun <warning descr="'blah()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">blah</warning>() {}
  }
}

@NonExtendable
class <warning descr="'SomeClass' is marked with '@ApiStatus.NonExtendable', but it cannot be extended">SomeClass</warning> {}

@NonExtendable
enum class <warning descr="'EnumClass' is marked with '@ApiStatus.NonExtendable', but it cannot be extended">EnumClass</warning> { BLACK, WHITE }

@NonExtendable
data class <warning descr="'DataClass' is marked with '@ApiStatus.NonExtendable', but it cannot be extended">DataClass</warning>(val name: String)
