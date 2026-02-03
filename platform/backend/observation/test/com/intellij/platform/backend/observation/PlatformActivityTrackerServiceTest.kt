// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.observation

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.Nls
import org.junit.jupiter.api.RepeatedTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random.Default.nextInt

class PlatformActivityTrackerServiceTest {


  object Key1 : ActivityKey {
    override val presentableName: @Nls String = "actor1"
  }

  object Key2 : ActivityKey {
    override val presentableName: @Nls String = "actor2"
  }

  object Key3 : ActivityKey {
    override val presentableName: @Nls String = "actor3"
  }

  val allKeys = arrayOf(Key1, Key2, Key3)

  fun chooseRandomActor(): ActivityKey {
    val index = nextInt(0, allKeys.size)
    return allKeys[index]
  }

  private fun CoroutineScope.createConfigurator(service: PlatformActivityTrackerService): CompletableJob {
    val j = Job()
    val actor = chooseRandomActor()
    launch {
      j.join()
      repeat(5) {
        service.trackConfigurationActivity(actor) {
          delay(10)
        }
      }
    }
    return j
  }

  private fun CoroutineScope.createAwaiter(service: PlatformActivityTrackerService): CompletableJob {
    val j = Job()
    val actor = chooseRandomActor()
    launch {
      j.join()
      repeat(5) {
        service.awaitConfiguration(actor)
      }
    }
    return j
  }

  // this test checks the absence of deadlocks in `service.awaitConfiguration`
  // and that internal invariants of `PlatformActivityTrackerService` are not violated
  // the execution is nondeterministic because of coroutines dispatcher and random shuffle
  @RepeatedTest(50)
  fun test(): Unit = runBlocking {
    withTimeout(10_000) {
      val service = PlatformActivityTrackerService(this)
      coroutineScope {
        val actors = (1..5).map { createConfigurator(service) }
        val waiters = (1..5).map { createAwaiter(service) }
        (actors + waiters).shuffled().map { it.complete() }
      }
    }
  }

  // this test asserts that the configurtion flow only fluctuates between true and false,
  // and it ends in a correct state of false, meaning that the configuration is complete
  @RepeatedTest(50)
  fun flowTest() : Unit = runBlocking {
    withTimeout(10_000) {
      val service = PlatformActivityTrackerService(this)
      val flow = service.configurationFlow
      assert(!flow.value)
      // we start with 1 because the flow would immediately emit 'false', which shall decrement the value
      val counter = AtomicInteger(1)
      coroutineScope {
        val launchedJob = launch {
          flow.collect { currentConfigState ->
            val counterValue = if (currentConfigState) {
              counter.getAndIncrement()
            } else {
              counter.decrementAndGet()
            }
            assert(counterValue == 1 || counterValue == 0) { "Counter value: $counterValue" }
          }
        }
        coroutineScope {
          val actors = (1..5).map { createConfigurator(service) }
          val waiters = (1..5).map { createAwaiter(service) }
          (actors + waiters).shuffled().map { it.complete() }
        }
        launchedJob.cancel()
      }
      assert(!flow.value)
    }
  }
}