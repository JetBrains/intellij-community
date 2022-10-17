// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.codeInsight.navigation.actions.navigateRequest
import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.impl.children
import com.intellij.ide.navbar.ui.FloatingModeHelper
import com.intellij.ide.navbar.vm.NavBarPopupVm
import com.intellij.ide.navbar.vm.NavBarVm
import com.intellij.ide.navbar.vm.NavBarVmItem
import com.intellij.ide.navbar.vm.PopupResult.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

internal class NavBarVmImpl(
  cs: CoroutineScope,
  private val project: Project,
  initialItems: List<NavBarVmItem>,
  activityFlow: Flow<Unit>,
) : NavBarVm {

  private val _items: MutableStateFlow<List<NavBarVmItem>> = MutableStateFlow(initialItems)
  override val items: StateFlow<List<NavBarVmItem>> = _items.asStateFlow()

  private val _itemEvents = MutableSharedFlow<ItemEvent>(replay = 1, onBufferOverflow = DROP_OLDEST)

  // Flag to block external model changes while user click is being processed
  private val skipFocusUpdates = AtomicBoolean(false)

  init {
    cs.launch(Dispatchers.Default) {
      activityFlow.collectLatest {
        if (skipFocusUpdates.get()) {
          return@collectLatest
        }
        val items = focusModel(project)
        if (items.isNotEmpty()) {
          _items.value = items
        }
      }
    }
    cs.launch(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {
      _itemEvents.collectLatest { e ->
        when (e) {
          is ItemEvent.Select -> freezeModelAndInvoke {
            handleItemSelected(e.item)
            FloatingModeHelper.hideHint(false)
          }
          is ItemEvent.Activate -> navigateTo(project, e.item)
        }
      }
    }
  }

  override val popup = MutableSharedFlow<Pair<NavBarVmItem, NavBarPopupVm>>(replay = 1, onBufferOverflow = DROP_OLDEST)

  override fun selectItem(item: NavBarVmItem) {
    _itemEvents.tryEmit(ItemEvent.Select(item))
  }

  override fun activateItem(item: NavBarVmItem) {
    _itemEvents.tryEmit(ItemEvent.Activate(item))
  }

  override fun selectTail() {
    val items = _items.value
    val i = (items.size - 2).coerceAtLeast(0)
    val item = items[i]
    _itemEvents.tryEmit(ItemEvent.Select(item))
  }

  // Run body with no external model changes allowed
  private suspend fun freezeModelAndInvoke(body: suspend () -> Unit) {
    check(!skipFocusUpdates.getAndSet(true))
    try {
      body()
    }
    finally {
      check(skipFocusUpdates.getAndSet(false))
    }
  }

  private suspend fun handleItemSelected(item: NavBarVmItem) {
    var items = _items.value
    var selectedIndex = items.indexOf(item).takeUnless { it < 0 } ?: return
    var children = item.children() ?: return

    while (true) {
      // Popup with [children] should be displayed for user at [selectedItem] item
      // Empty [children] is an illegal case, popup navigation ends
      if (children.isEmpty()) {
        return
      }

      // Force suspend to render new navbar for proper popup positioning
      yield()

      val popupResult = suspendCancellableCoroutine { continuation ->
        val nextItem = items.getOrNull(selectedIndex + 1)
        popup.tryEmit(Pair(items[selectedIndex], NavBarPopupVmImpl(children, nextItem, continuation)))
      }

      when (popupResult) {
        PopupResultCancel -> {
          FloatingModeHelper.hideHint(true)
          return
        }
        PopupResultLeft -> {
          if (selectedIndex > 0) {
            selectedIndex--
            children = items[selectedIndex].children() ?: return
          }
        }
        PopupResultRight -> {
          if (selectedIndex < items.size - 1) {
            selectedIndex++
          }
          val localChildren = items[selectedIndex].children() ?: return
          if (localChildren.isEmpty()) {
            selectedIndex--
          }
          else {
            children = localChildren
          }
        }
        is PopupResultSelect -> {
          val selectedChild = popupResult.item
          val expandResult = autoExpand(selectedChild) ?: return
          when (expandResult) {
            is ExpandResult.NavigateTo -> {
              navigateTo(project, expandResult.target)
              return
            }
            is ExpandResult.NextPopup -> {
              val newModel = items.slice(0..selectedIndex) + expandResult.expanded
              _items.value = newModel
              items = newModel
              selectedIndex = newModel.indices.last
              children = expandResult.children
            }
          }
        }
      }
    }
  }
}

private sealed interface ItemEvent {
  class Select(val item: NavBarVmItem) : ItemEvent
  class Activate(val item: NavBarVmItem) : ItemEvent
}

private sealed interface ExpandResult {
  class NavigateTo(val target: NavBarVmItem) : ExpandResult
  class NextPopup(val expanded: List<NavBarVmItem>, val children: List<NavBarVmItem>) : ExpandResult
}

private suspend fun autoExpand(child: NavBarVmItem): ExpandResult? {
  var expanded = emptyList<NavBarVmItem>()
  var currentItem = child
  var (children, navigateOnClick) = currentItem.fetch(childrenSelector, NavBarItem::navigateOnClick) ?: return null

  if (children.isEmpty() || navigateOnClick) {
    return ExpandResult.NavigateTo(currentItem)
  }

  while (true) {
    // [currentItem] -- is being evaluated
    // [expanded] -- list of the elements starting from [child] argument and up to [currentItem]. Both exclusively
    // [children] -- children of the [currentItem]
    // [showPopup] -- if [currentItem]'s children should be shown as a popup

    // No automatic navigation in this cycle!
    // It is only allowed as reaction to a users click
    // at the popup item, i.e. at first iteration before while-cycle

    when (children.size) {
      0 -> {
        // No children, [currentItem] is an only leaf on its branch, but no auto navigation allowed
        // So returning the previous state
        return ExpandResult.NextPopup(expanded, listOf(currentItem))
      }
      1 -> {
        if (navigateOnClick) {
          // [currentItem] is navigation target regardless of its children count, but no auto navigation allowed
          // So returning the previous state
          return ExpandResult.NextPopup(expanded, listOf(currentItem))
        }
        else {
          // Performing auto-expand, keeping invariant
          expanded = expanded + currentItem
          currentItem = children.single()
          val fetch = currentItem.fetch(childrenSelector, NavBarItem::navigateOnClick) ?: return null
          children = fetch.first
          navigateOnClick = fetch.second
        }
      }
      else -> {
        // [currentItem] has several children, so return it with current [expanded] trace.
        return ExpandResult.NextPopup(expanded + currentItem, children)
      }
    }
  }
}

private suspend fun navigateTo(project: Project, item: NavBarVmItem) {
  val navigationRequest = item.fetch(NavBarItem::navigationRequest)
                          ?: return
  withContext(Dispatchers.EDT) {
    navigateRequest(project, navigationRequest)
  }
}

private suspend fun NavBarVmItem.children(): List<NavBarVmItem>? {
  return fetch(childrenSelector)
}

private suspend fun <T> NavBarVmItem.fetch(selector: NavBarItem.() -> T): T? {
  return readAction {
    pointer.dereference()?.selector()
  }
}

private suspend fun <T1, T2> NavBarVmItem.fetch(
  selector1: NavBarItem.() -> T1,
  selector2: NavBarItem.() -> T2
): Pair<T1, T2>? = fetch { Pair(selector1(), selector2()) }

private val childrenSelector: NavBarItem.() -> List<NavBarVmItem> = {
  children().toVmItems()
}
