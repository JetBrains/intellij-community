package plugin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.NonExtendable

@ApiStatus.NonExtendable
fun <warning descr="Method 'foo()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">foo</warning>() {}

@NonExtendable
fun <warning descr="Method 'bar()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">bar</warning>() {}

class NotOpenClass {
  @NonExtendable
  fun <warning descr="Method 'foo()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">foo</warning>() {}
}

@NonExtendable
open class SomeOpenClass {
  @NonExtendable
  open fun <warning descr="Annotation '@ApiStatus.NonExtendable' is redundant">someOpenMethod</warning>() {}

  open fun someAnotherOpenMethod() {}
}

open class OpenClass {
  @NonExtendable
  private fun <warning descr="Method 'foo()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">foo</warning>() {}

  @NonExtendable
  fun <warning descr="Method 'bar()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">bar</warning>() {}

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
    private fun <warning descr="Method 'blah()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">blah</warning>() {}
  }
}

@NonExtendable
class <warning descr="Class 'SomeClass' is marked with '@ApiStatus.NonExtendable', but it cannot be extended">SomeClass</warning> {}

@NonExtendable
enum class <warning descr="Class 'EnumClass' is marked with '@ApiStatus.NonExtendable', but it cannot be extended">EnumClass</warning> { BLACK, WHITE }

@NonExtendable
data class <warning descr="Class 'DataClass' is marked with '@ApiStatus.NonExtendable', but it cannot be extended">DataClass</warning>(val name: String)
