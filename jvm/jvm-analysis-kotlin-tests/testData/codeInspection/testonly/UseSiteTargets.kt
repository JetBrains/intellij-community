package test

import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

// IDEA-269740 need better support for UAST properties
@get:[TestOnly VisibleForTesting]
val x = 0

@get:[TestOnly]
val y = 0

@get:TestOnly
val z = 0

fun doSomething(q: Int) = q

fun main() {
  doSomething(<warning descr="Test-only method is called in production code">y</warning>)
  doSomething(<warning descr="Test-only method is called in production code">z</warning>)
}