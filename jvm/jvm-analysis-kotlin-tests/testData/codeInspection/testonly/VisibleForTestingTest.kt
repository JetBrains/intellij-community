package test

import org.jetbrains.annotations.VisibleForTesting
import testapi.VisibleForTestingTestApi

object VisibleForTestingTest {
  val foobar = 0
    @VisibleForTesting get() = field

  fun main() {
    foobar
    <warning descr ="Test-only method is called in production code">VisibleForTestingTestApi.foo</warning>
    <warning descr ="Test-only method is called in production code">VisibleForTestingTestApi.bar()</warning>
  }
}