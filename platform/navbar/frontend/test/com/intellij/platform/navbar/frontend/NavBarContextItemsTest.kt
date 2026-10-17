// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend

import com.intellij.platform.navbar.NavBarItemPresentationData
import com.intellij.platform.navbar.NavBarVmItem
import com.intellij.platform.navbar.frontend.vm.NavBarVm
import com.intellij.platform.navbar.frontend.vm.impl.NavBarVmImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.ref.GCWatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * A static navigation bar keeps its last items while the context gives none. An unload of a plugin replaces them, so the bar keeps no
 * object that its old items showed, also when an update or a later request races with the unload.
 */
internal class NavBarContextItemsTest {
  private val updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val unloads = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  @Test
  fun `an empty context keeps the items, and an unload gives the default model and lets the old items go`(): Unit = timeoutRunBlocking {
    val context = AtomicReference<List<NavBarVmItem>?>()
    withVm(initialItems = DEFAULT, navBarContextItems(updates, unloads, contextModel = { context.get() }, ::defaultModel)) { vm ->
      val payload = showItemWithWatchedPayload(context)
      vm.awaitItems(SHOWN)

      context.set(emptyList())
      check(updates.tryEmit(Unit))
      repeat(SETTLE_ROUNDS) { yield() }
      assertThat(vm.texts()).containsExactly(SHOWN)

      check(unloads.tryEmit(Unit))
      vm.awaitItems(DEFAULT)
      repeat(SETTLE_ROUNDS) { yield() }
      payload.ensureCollected()
    }
  }

  @Test
  fun `an unload cancels an update in flight, so the items of its old context never follow the reset`(): Unit = timeoutRunBlocking {
    val updateStarted = CompletableDeferred<Unit>()
    val releaseUpdate = CompletableDeferred<Unit>()
    val heldUpdateCancelled = CompletableDeferred<Unit>()
    val contextCalls = AtomicInteger()
    val contextModel: suspend () -> List<NavBarVmItem>? = {
      if (contextCalls.incrementAndGet() == 1) {
        updateStarted.complete(Unit)
        try {
          releaseUpdate.await()
        }
        catch (e: CancellationException) {
          heldUpdateCancelled.complete(Unit)
          throw e
        }
        listOf(TestItem(SHOWN))
      }
      else {
        null
      }
    }
    withVm(initialItems = SHOWN, navBarContextItems(updates, unloads, contextModel, ::defaultModel)) { vm ->
      check(updates.tryEmit(Unit))
      updateStarted.await()

      check(unloads.tryEmit(Unit))
      vm.awaitItems(DEFAULT)
      releaseUpdate.complete(Unit)
      repeat(SETTLE_ROUNDS) { yield() }

      assertThat(heldUpdateCancelled.isCompleted).describedAs("the unload cancels the update in flight").isTrue()
      assertThat(vm.texts()).containsExactly(DEFAULT)
    }
  }

  @Test
  fun `a request that cancels a reset leaves the reset pending, so a later empty context gives the default model`(): Unit =
    timeoutRunBlocking {
      val resetStarted = CompletableDeferred<Unit>()
      val heldResetCancelled = CompletableDeferred<Unit>()
      val defaultModelCalls = AtomicInteger()
      val firstCallHeld: suspend () -> List<NavBarVmItem> = {
        if (defaultModelCalls.incrementAndGet() == 1) {
          resetStarted.complete(Unit)
          try {
            awaitCancellation()
          }
          catch (e: CancellationException) {
            heldResetCancelled.complete(Unit)
            throw e
          }
        }
        defaultModel()
      }
      withVm(initialItems = SHOWN, navBarContextItems(updates, unloads, contextModel = { null }, firstCallHeld)) { vm ->
        check(unloads.tryEmit(Unit))
        resetStarted.await()

        check(updates.tryEmit(Unit))
        heldResetCancelled.await()
        vm.awaitItems(DEFAULT)
      }
    }

  @Test
  fun `an unload with a live context gives the items of that context`(): Unit = timeoutRunBlocking {
    val context = AtomicReference<List<NavBarVmItem>?>(listOf(TestItem(LIVE)))
    withVm(initialItems = SHOWN, navBarContextItems(updates, unloads, contextModel = { context.get() }, ::defaultModel)) { vm ->
      check(unloads.tryEmit(Unit))
      vm.awaitItems(LIVE)
    }
  }

  /** Runs [action] with a bar over [contextItems] that subscribed to the requests, and cancels the bar after it. */
  private suspend fun withVm(initialItems: String, contextItems: Flow<List<NavBarVmItem>>, action: suspend (NavBarVm) -> Unit) {
    val context = currentCoroutineContext()
    val vmScope = CoroutineScope(context + Job(context.job))
    try {
      val vm = NavBarVmImpl(vmScope, listOf(TestItem(initialItems)), contextItems)
      updates.subscriptionCount.first { it > 0 }
      unloads.subscriptionCount.first { it > 0 }
      action(vm)
    }
    finally {
      vmScope.cancel()
    }
  }

  /** Not a suspend function, so no coroutine frame keeps the item or its payload after the return. */
  private fun showItemWithWatchedPayload(context: AtomicReference<List<NavBarVmItem>?>): GCWatcher {
    val payload = Any()
    context.set(listOf(TestItem(SHOWN, payload)))
    check(updates.tryEmit(Unit))
    return GCWatcher.tracking(payload)
  }
}

private fun defaultModel(): List<NavBarVmItem> = listOf(TestItem(DEFAULT))

private suspend fun NavBarVm.awaitItems(text: String) {
  items.first { items -> items.map { it.presentation.text } == listOf(text) }
}

private fun NavBarVm.texts(): List<String> = items.value.map { it.presentation.text }

/** An item of the bar, with a [payload] that stands for the object it shows, such as a file of a plugin. */
private class TestItem(private val text: String, @Suppress("unused") private val payload: Any? = null) : NavBarVmItem {
  override val presentation: NavBarItemPresentationData = NavBarItemPresentationData(
    icon = null, text = text, popupText = null, textAttributes = null, hasContainingFile = false, isModuleContentRoot = false,
  )

  override suspend fun children(): List<NavBarVmItem>? = null

  override fun equals(other: Any?): Boolean = other is TestItem && other.text == text

  override fun hashCode(): Int = text.hashCode()
}

private const val DEFAULT = "the project"
private const val SHOWN = "a file of a plugin"
private const val LIVE = "a file of the live context"
private const val SETTLE_ROUNDS = 10
