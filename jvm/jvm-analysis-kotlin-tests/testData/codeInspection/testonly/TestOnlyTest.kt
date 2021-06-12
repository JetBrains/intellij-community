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
  @<warning descr="@VisibleForTesting makes little sense on @TestOnly code">VisibleForTesting</warning>
  fun aStringMethod(): String = "Foo"
}

fun main() {
  val foo1 = <warning descr="Test-only class is referenced in production code">TestOnlyTest</warning>()
  val foo2 = test.<warning descr="Test-only class is referenced in production code">TestOnlyTest</warning>()
  val foo3 = <warning descr="Test-only class is referenced in production code">TestOnlyTest</warning>().nonTestField
  val bar = foo1.<warning descr="Test-only method is called in production code">aField</warning>
  foo1.<warning descr="Test-only method is called in production code">aMethod</warning>(bar)
  TestOnlyTest::<warning descr="Test-only method is called in production code">aMethod</warning>.invoke(foo2, foo3)
  test.TestOnlyTest::<warning descr="Test-only method is called in production code">aMethod</warning>.invoke(foo2, foo3)
}

@TestOnly
fun testOnly() {
  val foo1 = TestOnlyTest()
  val foo2 = test.TestOnlyTest()
  val foo3 = TestOnlyTest().nonTestField
  val bar = foo1.aField
  foo1.aMethod(bar)
  TestOnlyTest::aMethod.invoke(foo2, foo3)
  test.TestOnlyTest::aMethod.invoke(foo2, foo3)
}