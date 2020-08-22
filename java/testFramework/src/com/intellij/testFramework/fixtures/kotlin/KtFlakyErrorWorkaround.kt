package com.intellij.testFramework.fixtures.kotlin

import com.intellij.openapi.diagnostic.logger

class KtFlakyErrorWorkaround(private val setUp: () -> Unit,
                             private val tearDown: () -> Unit,
                             private val repeatCount: Int = 5) {

  fun wrapFlaky(actualMessagePattern: Regex, body: () -> Unit): Unit =
    repeatTest({ e -> e is AssertionError && e.actual.contains(actualMessagePattern) }, body)

  fun wrapFlaky(actualMessagePattern: Regex, body: Runnable): Unit = wrapFlaky(actualMessagePattern) { body.run() }

  fun repeatTest(repeatCondition: (Throwable) -> Boolean = { true }, body: () -> Unit) {
    var repeat: Boolean
    var repeatedTimes = 0
    do {
      try {
        body()
        repeat = false
      }
      catch (e: Throwable) {
        if (repeatCondition(e)) {
          logger<KtFlakyErrorWorkaround>().warn("Workaround hit! ${e.javaClass} on try: $repeatedTimes")
          repeatedTimes++
          repeat = true
          if (repeatedTimes > repeatCount) {
            logger<KtFlakyErrorWorkaround>().error("Workaround: hit too many times: $repeatedTimes, max is $repeatCount")
            throw e
          }
          tearDown()
          setUp()
        }
        else {
          throw e
        }
      }
    }
    while (repeat)
  }

  private val AssertionError.actual: String
    get() = when (this) {
      is org.junit.ComparisonFailure -> this.actual
      is junit.framework.ComparisonFailure -> this.actual
      else -> ""
    }

}