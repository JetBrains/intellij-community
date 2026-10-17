// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend

import com.intellij.platform.navbar.NavBarItemPresentationData
import com.intellij.platform.navbar.NavBarVmItem
import com.intellij.platform.navbar.frontend.vm.impl.NavBarVmImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.ref.GCWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A static navigation bar keeps its last items while the context gives none. An unload of a plugin replaces them with the default
 * model, so the bar keeps no object that its old items showed.
 */
internal class NavBarContextItemsTest {
  @Test
  fun `an empty context keeps the items, and an unload gives the default model and lets the old items go`(): Unit = timeoutRunBlocking {
    val updates = MutableSharedFlow<List<NavBarVmItem>>(extraBufferCapacity = 1)
    val unloads = MutableSharedFlow<Unit>(replay = 1)
    val vmScope = CoroutineScope(coroutineContext + Job(coroutineContext.job))
    try {
      val contextItems = navBarContextItems(updates, unloads) { listOf(TestItem(DEFAULT)) }
      val vm = NavBarVmImpl(vmScope, initialItems = listOf(TestItem(DEFAULT)), contextItems)
      updates.subscriptionCount.first { it > 0 }
      val payload = emitItemWithWatchedPayload(updates)
      vm.items.first { items -> items.map { it.presentation.text } == listOf(SHOWN) }

      check(updates.tryEmit(emptyList()))
      repeat(SETTLE_ROUNDS) { yield() }
      assertThat(vm.items.value.map { it.presentation.text }).containsExactly(SHOWN)

      check(unloads.tryEmit(Unit))
      vm.items.first { items -> items.map { it.presentation.text } == listOf(DEFAULT) }
      repeat(SETTLE_ROUNDS) { yield() }
      payload.ensureCollected()
    }
    finally {
      vmScope.cancel()
    }
  }

  /** Not a suspend function, so no coroutine frame keeps the item or its payload after the return. */
  private fun emitItemWithWatchedPayload(updates: MutableSharedFlow<List<NavBarVmItem>>): GCWatcher {
    val payload = Any()
    check(updates.tryEmit(listOf(TestItem(SHOWN, payload))))
    return GCWatcher.tracking(payload)
  }
}

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
private const val SETTLE_ROUNDS = 10
