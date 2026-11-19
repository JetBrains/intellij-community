// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.tests

import com.intellij.platform.rpc.lite.LiteRemoteApiProviderHolder
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.assertErrorLogged
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread

@Timeout(30)
internal class LiteRemoteApiProviderHolderTest {
  @Test
  fun `provider is unavailable until installed`() {
    val holder = LiteRemoteApiProviderHolder()

    assertThat(holder.isConnected()).isFalse()
    assertThat(holder.tryResolve(LiteTestApiDescriptor)).isNull()
  }

  @Test
  fun `installation releases all waiting callers`(): Unit = timeoutRunBlocking {
    val holder = LiteRemoteApiProviderHolder()
    val requests = List(2) {
      async(start = CoroutineStart.UNDISPATCHED) {
        holder.awaitConnectionAndResolve(LiteTestApiDescriptor)
      }
    }
    assertThat(requests).allMatch { !it.isCompleted }
    assertThat(holder.isConnected()).isFalse()

    val api = LiteTestApi()
    holder.install(LiteTestApiProvider(api))

    assertThat(holder.isConnected()).isTrue()
    for (request in requests) {
      assertThat(request.await()).isSameAs(api)
    }
    assertThat(holder.tryResolve(LiteTestApiDescriptor)).isSameAs(api)
    assertThat(holder.awaitConnectionAndResolve(LiteTestApiDescriptor)).isSameAs(api)
  }

  @Test
  fun `cancelling a caller leaves the provider available`(): Unit = timeoutRunBlocking {
    val holder = LiteRemoteApiProviderHolder()
    val cancelledRequest = async(start = CoroutineStart.UNDISPATCHED) {
      holder.awaitConnectionAndResolve(LiteTestApiDescriptor)
    }
    val remainingRequest = async(start = CoroutineStart.UNDISPATCHED) {
      holder.awaitConnectionAndResolve(LiteTestApiDescriptor)
    }

    cancelledRequest.cancelAndJoin()

    assertThat(holder.isConnected()).isFalse()
    assertThat(remainingRequest.isCompleted).isFalse()
    val api = LiteTestApi()
    holder.install(LiteTestApiProvider(api))
    assertThat(remainingRequest.await()).isSameAs(api)
  }

  @Test
  fun `the first installed provider remains active`(): Unit = timeoutRunBlocking {
    val holder = LiteRemoteApiProviderHolder()
    val api = LiteTestApi()
    holder.install(LiteTestApiProvider(api))
    val error = assertErrorLogged<Throwable> {
      holder.install(LiteTestApiProvider(LiteTestApi()))
    }

    assertThat(error.message).contains("already installed")

    assertThat(holder.tryResolve(LiteTestApiDescriptor)).isSameAs(api)
    assertThat(holder.awaitConnectionAndResolve(LiteTestApiDescriptor)).isSameAs(api)
  }

  @Test
  fun `concurrent installers keep one provider and report the other`() {
    val holder = LiteRemoteApiProviderHolder()
    val apis = List(2) { LiteTestApi() }
    val barrier = CyclicBarrier(apis.size)
    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      val installers = apis.map { api ->
        thread {
          barrier.await()
          holder.install(LiteTestApiProvider(api))
        }
      }
      installers.forEach { it.join() }
    }

    assertThat(error.message).contains("already installed")
    assertThat(holder.isConnected()).isTrue()
    assertThat(holder.tryResolve(LiteTestApiDescriptor)).isIn(apis)
  }

  @Test
  fun `the same provider installed twice is silent`(): Unit = timeoutRunBlocking {
    val holder = LiteRemoteApiProviderHolder()
    val api = LiteTestApi()
    val provider = LiteTestApiProvider(api)
    holder.install(provider)
    holder.install(provider)

    assertThat(holder.isConnected()).isTrue()
    assertThat(holder.tryResolve(LiteTestApiDescriptor)).isSameAs(api)
  }

  @Test
  fun `uninstall drops the provider`(): Unit = timeoutRunBlocking {
    val holder = LiteRemoteApiProviderHolder()
    holder.install(LiteTestApiProvider(LiteTestApi()))

    holder.uninstall()

    assertThat(holder.isConnected()).isFalse()
    assertThat(holder.tryResolve(LiteTestApiDescriptor)).isNull()
    val request = async(start = CoroutineStart.UNDISPATCHED) {
      holder.awaitConnectionAndResolve(LiteTestApiDescriptor)
    }
    assertThat(request.isCompleted).isFalse()
    request.cancelAndJoin()
  }

  @Test
  fun `a provider installed again releases the callers that waited`(): Unit = timeoutRunBlocking {
    val holder = LiteRemoteApiProviderHolder()
    holder.install(LiteTestApiProvider(LiteTestApi()))
    holder.uninstall()
    val request = async(start = CoroutineStart.UNDISPATCHED) {
      holder.awaitConnectionAndResolve(LiteTestApiDescriptor)
    }
    assertThat(request.isCompleted).isFalse()

    val api2 = LiteTestApi()
    holder.install(LiteTestApiProvider(api2))

    assertThat(holder.isConnected()).isTrue()
    assertThat(request.await()).isSameAs(api2)
    assertThat(holder.tryResolve(LiteTestApiDescriptor)).isSameAs(api2)
  }

  @Test
  fun `a missing API stays unavailable after installation`(): Unit = timeoutRunBlocking {
    val holder = LiteRemoteApiProviderHolder()
    holder.install(LiteTestApiProvider(null))

    assertThat(holder.isConnected()).isTrue()
    assertThat(holder.tryResolve(LiteTestApiDescriptor)).isNull()
    assertThrows<IllegalStateException> {
      holder.awaitConnectionAndResolve(LiteTestApiDescriptor)
    }
  }

  @Test
  fun `provider failures reach the caller`(): Unit = timeoutRunBlocking {
    val holder = LiteRemoteApiProviderHolder()
    val failure = IllegalStateException("The test provider failed")
    holder.install(LiteTestApiProvider(null, failure))

    assertThat(assertThrows<IllegalStateException> {
      holder.tryResolve(LiteTestApiDescriptor)
    }).isSameAs(failure)
    assertThat(assertThrows<IllegalStateException> {
      holder.awaitConnectionAndResolve(LiteTestApiDescriptor)
    }).isSameAs(failure)
  }
}
