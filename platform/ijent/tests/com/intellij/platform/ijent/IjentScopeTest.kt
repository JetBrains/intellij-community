// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.testFramework.bodyLimitedCoroutineScope
import com.intellij.platform.eel.testFramework.executeAndCollectLoggedErrors
import com.intellij.platform.eel.testFramework.executeAndReturnLoggedError
import com.intellij.platform.ijent.IjentUnavailableException.ClosedByApplication
import com.intellij.platform.ijent.IjentUnavailableException.CommunicationFailure
import com.intellij.platform.ijent.spi.IjentThreadPool
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.DebugAttachDetectorArgs
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class IjentScopeTest {
  private fun differentDispatchersTest(body: suspend CoroutineScope.() -> Unit): List<DynamicNode> {
    val timeout =
      if (DebugAttachDetectorArgs.isAttached() && System.getenv("TEAMCITY_VERSION") != null) Duration.INFINITE
      else 20.seconds
    return buildList {
      add(DynamicTest.dynamicTest("blocking dispatcher") {
        timeoutRunBlocking(timeout, "blocking dispatcher") {
          body()
        }
      })

      add(DynamicTest.dynamicTest("single-threaded dispatcher") {
        timeoutRunBlocking(timeout, "single-threaded dispatcher", Dispatchers.IO.limitedParallelism(1)) {
          body()
        }
      })

      add(DynamicTest.dynamicTest("multi-threaded dispatcher") {
        timeoutRunBlocking(timeout, "multi-threaded dispatcher", Dispatchers.IO) {
          body()
        }
      })
    }
  }

  @TestFactory
  fun `the dispatcher is always replaced to IjentThreadPool`() = differentDispatchersTest {
    bodyLimitedCoroutineScope {
      ParentOfIjentScopes(this).createIjentScope("IjentScopeTest").s
        .launch {
          repeat(123) {
            val err = executeAndReturnLoggedError(collectMessagesWithoutExceptions = true) {
              IjentThreadPool.checkCurrentThreadIsInPool()
            }
            if (err != null) {
              throw err
            }
            yield()
          }
        }
        .join()
    }
  }

  @TestFactory
  fun `any failure in any coroutine of ijent terminates the whole session`() = differentDispatchersTest {
    val loggedErrors = mutableListOf<Throwable>()
    lateinit var ijentScope: IjentScope
    val caughtError: IllegalStateException
    executeAndCollectLoggedErrors(loggedErrors, collectMessagesWithoutExceptions = true) {
      caughtError = shouldThrow<IllegalStateException> {
        coroutineScope {
          ijentScope = ParentOfIjentScopes(this).createIjentScope("IjentScopeTest")
          ijentScope.s.launch {
            delay(100.milliseconds)
            error("oops")
          }
        }
      }
    }
    caughtError.message shouldBe "oops"

    val rethrownErr = shouldThrow<IllegalStateException> {
      ijentScope.wrapErrors {
        ijentScope.s.async { delay(1.seconds) }.await()
      }
    }
    rethrownErr.message shouldBe "oops"

    // In this test we don't care if this particular error is actually logged or not.
    loggedErrors.removeAll {
      it.javaClass == rethrownErr.javaClass && it.message == rethrownErr.message
    }
    withClue("No unexpected errors logged") {
      loggedErrors.shouldBeEmpty()
    }
  }

  @TestFactory
  fun `a completed child does not hide the exit reason`() = differentDispatchersTest {
    bodyLimitedCoroutineScope {
      val ijentScope = ParentOfIjentScopes(this).createIjentScope("IjentScopeTest")
      val firstChildCanFinish = CompletableDeferred<Unit>()
      // The children run in `IjentThreadPool`. The failing child can cancel them before they start,
      // and then a child with `CoroutineStart.DEFAULT` never runs its body.
      val firstChild = ijentScope.s.launch(start = CoroutineStart.ATOMIC) {
        withContext(NonCancellable) {
          firstChildCanFinish.await()
        }
      }
      val expected = ClosedByApplication("The session closed", null)
      ijentScope.s.launch(start = CoroutineStart.ATOMIC) {
        try {
          awaitCancellation()
        }
        finally {
          withContext(NonCancellable) {
            firstChild.join()
            delay(50.milliseconds)
            ijentScope.destroy(expected, isRootCause = true)
          }
        }
      }

      // `resolveExitReason` waits only in a scope that shuts down.
      ijentScope.s.launch {
        error("A failure without destroy")
      }.join()

      val resolved = async(start = CoroutineStart.UNDISPATCHED) {
        ijentScope.resolveExitReason(timeout = 1.seconds)
      }
      firstChildCanFinish.complete(Unit)
      resolved.await() shouldBe expected
    }
  }

  @TestFactory
  fun `resolveExitReason returns null immediately in an alive scope`() = differentDispatchersTest {
    bodyLimitedCoroutineScope {
      val ijentScope = ParentOfIjentScopes(this).createIjentScope("IjentScopeTest")
      val child = ijentScope.s.launch { awaitCancellation() }

      val resolved = withTimeout(1.seconds) {
        ijentScope.resolveExitReason(Duration.INFINITE)
      }
      resolved.shouldBeNull()

      val safeDeferred = ijentScope.toSafeDeferred(CompletableDeferred<Unit>().apply {
        completeExceptionally(IllegalStateException("An ordinary API failure"))
      })
      val thrown = withTimeout(1.seconds) {
        shouldThrow<SafeDeferred.FailedDeferred> { safeDeferred.await() }
      }
      thrown.cause.shouldBeInstanceOf<IllegalStateException>().message shouldBe "An ordinary API failure"

      child.isActive shouldBe true
      ijentScope.destroy(ClosedByApplication("The test is over", null), isRootCause = true)
    }
  }

  @TestFactory
  fun `a later root cause is suppressed in the first root cause`() = differentDispatchersTest {
    val first = ClosedByApplication("The first root cause", null)
    val second = CommunicationFailure("The second root cause", null)
    lateinit var ijentScope: IjentScope

    shouldNotThrowAny {
      coroutineScope {
        ijentScope = ParentOfIjentScopes(this).createIjentScope("IjentScopeTest")
        ijentScope.destroy(first, isRootCause = true)
        ijentScope.destroy(second, isRootCause = true)
      }
    }

    ijentScope.resolveExitReason() shouldBe first
    first.suppressed.toList() shouldBe listOf(second)
  }

  @TestFactory
  fun `IjentScope rethrows the root cause`() = differentDispatchersTest {
    val rightErrorMessage = "This is the right error (${Random.nextInt()})"

    lateinit var functionThatWorksLikeAnyEelApiMethod: suspend () -> Unit

    shouldNotThrowAny {
      coroutineScope {
        val ijentScope = ParentOfIjentScopes(this).createIjentScope("IjentProcessUtilTest")

        val deferred = ijentScope.s.async {
          delay(10.seconds)
        }

        functionThatWorksLikeAnyEelApiMethod = {
          ijentScope.wrapErrors {
            deferred.await()
          }
        }

        ijentScope.s.launch {
          launch {  // Just a nested coroutine to
            delay(10.milliseconds)
            throw CommunicationFailure("This error should not propagate", null)
          }
        }

        ijentScope.s.launch {
          delay(10.milliseconds)
          throw IllegalStateException("This error should not propagate either", null)
        }

        ijentScope.s.launch(start = CoroutineStart.UNDISPATCHED) {
          try {
            delay(200.milliseconds)
          }
          catch (ex: Throwable) {
            throw ex
          }
          finally {
            val err = ClosedByApplication(rightErrorMessage, null)
            ijentScope.destroy(err, isRootCause = true)
            throw CommunicationFailure("And even this error should not propagate", null)
          }
        }
      }
    }

    val errorFromExternalCall = shouldThrow<ClosedByApplication> {
      functionThatWorksLikeAnyEelApiMethod()
    }
    errorFromExternalCall.message shouldBe rightErrorMessage
  }

  @TestFactory
  fun `IjentScope resolveExitReason does not wait longer than its children lifetime`() = differentDispatchersTest {
    val theChildFinished = AtomicBoolean(false)
    val loggedErrors = mutableListOf<Throwable>()

    val ijentScope: IjentScope
    // TestUncaughtExceptionHandler would fail tests without the empty CoroutineExceptionHandler
    withContext(CoroutineExceptionHandler { _, _ -> }) {
      executeAndCollectLoggedErrors(loggedErrors) {
        supervisorScope {
          ijentScope = ParentOfIjentScopes(this).createIjentScope("IjentProcessUtilTest")
          ijentScope.s.launch(start = CoroutineStart.ATOMIC) {
            try {
              error("oops")
            }
            finally {
              theChildFinished.set(true)
            }
          }
        }
      }
    }

    // 1 second timeout should be enough for any glitches. No race with `resolveExitReason` because of the infinite timeout there.
    val resolvedError = withTimeout(1.seconds) {
      ijentScope.resolveExitReason(Duration.INFINITE)
    }
    withClue("Since destroy() is not called, resolveExitReason() returns null") {
      resolvedError.shouldBeNull()
    }

    loggedErrors.forAll {
      it.message shouldBe "oops"
    }
    // For some reason, the error is logged twice. It's a minor issue.
    loggedErrors shouldHaveAtLeastSize 1
  }

  @TestFactory
  fun `cancellation of IjentScope is prohibited`() = differentDispatchersTest {
    lateinit var ijentScope: IjentScope
    val loggedError = executeAndReturnLoggedError {
      supervisorScope {
        ijentScope = ParentOfIjentScopes(this).createIjentScope("test")
        ijentScope.s.cancel("oops")
      }
    }

    loggedError?.message shouldBe "Cancelling IjentScope is prohibited, use IjentScope.destroy() instead"

    shouldThrow<ClosedByApplication> {
      ijentScope.wrapErrors {
        ijentScope.s.async { }.await()
      }
    }
  }

  @TestFactory
  fun `cancellation of the parent is not reported as a prohibited cancellation`() = differentDispatchersTest {
    lateinit var ijentScope: IjentScope
    val loggedError = executeAndReturnLoggedError {
      val parentJob = Job()
      ijentScope = ParentOfIjentScopes(CoroutineScope(parentJob)).createIjentScope("test")
      ijentScope.s.launch { awaitCancellation() }
      parentJob.cancelAndJoin()
    }

    loggedError.shouldBeNull()

    shouldThrow<ClosedByApplication> {
      ijentScope.wrapErrors {
        ijentScope.s.async { }.await()
      }
    }
  }

  @TestFactory
  fun `destroy after the scope completed keeps the exit reason`() = differentDispatchersTest {
    val uncaught = Collections.synchronizedList(mutableListOf<Throwable>())
    val loggedErrors = mutableListOf<Throwable>()
    lateinit var ijentScope: IjentScope
    executeAndCollectLoggedErrors(loggedErrors) {
      val parentJob = SupervisorJob()
      val parent = CoroutineScope(parentJob + CoroutineExceptionHandler { _, err -> uncaught += err })
      ijentScope = ParentOfIjentScopes(parent).createIjentScope("IjentScopeTest")
      ijentScope.s.launch { awaitCancellation() }
      parentJob.cancelAndJoin()

      // In production, gRPC reports UNAVAILABLE after the application has dropped the session.
      ijentScope.destroy(CommunicationFailure("A late failure", null), isRootCause = false)
    }

    val reason = ijentScope.resolveExitReason()
    reason.shouldBeInstanceOf<ClosedByApplication>()
    reason.suppressed.toList().shouldBeEmpty()
    loggedErrors.shouldBeEmpty()
    uncaught.shouldBeEmpty()
  }
}
