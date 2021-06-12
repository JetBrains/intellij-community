package test

import org.jetbrains.annotations.VisibleForTesting
import testapi.VisibleForTestingTestApi

object VisibleForTestingTest {
  val foobar = 0
    @VisibleForTesting get() = field

  fun main() {
    foobar
    VisibleForTestingTestApi.<warning descr="Test-only method is called in production code">foo</warning>
    VisibleForTestingTestApi.<warning descr="Test-only method is called in production code">bar</warning>()
  }
}