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
import kotlin.collections.map
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
}