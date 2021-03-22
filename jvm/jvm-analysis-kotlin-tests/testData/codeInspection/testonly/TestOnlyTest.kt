package test

import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

class TestOnlyTest @TestOnly constructor() {
  val nonTestField = 0

  var aField = 0
    @TestOnly get() = field

  @TestOnly
  fun aMethod(x: Int): Int = x

  @TestOnly
  @VisibleForTesting
  fun <warning descr = "@VisibleForTesting makes little sense on @TestOnly code" > aStringMethod < / warning >(): String = "Foo"
}

fun main() {
  val foo1 = <warning descr = "Test-only class is referenced in production code">TestOnlyTest()</warning>
  val foo2 = <warning descr = "Test-only class is referenced in production code">test.TestOnlyTest()</warning>
  val foo3 = <warning descr = "Test-only class is referenced in production code">TestOnlyTest()</warning>.nonTestField
  val bar = <warning descr = "Test-only method is called in production code">foo1.aField</warning>
  <warning descr ="Test-only method is called in production code">foo1.aMethod(bar)</warning>
  <warning descr ="Test-only method is called in production code">TestOnlyTest::aMethod</warning>.invoke(foo2, foo3)
  <warning descr ="Test-only method is called in production code">test.TestOnlyTest::aMethod</warning>.invoke(foo2, foo3)
}