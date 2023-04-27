// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.NavBarItemPresentation
import com.intellij.ide.navbar.impl.children
import com.intellij.ide.navbar.vm.NavBarItemVm
import com.intellij.ide.navbar.vm.NavBarVm
import com.intellij.ide.navbar.vm.NavBarVm.SelectionShift
import com.intellij.model.Pointer
import com.intellij.util.flow.zipWithNext
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal class NavBarVmImpl(
  cs: CoroutineScope,
  initialItems: List<NavBarVmItem>,
  contextItems: Flow<List<NavBarVmItem>>,
  private val requestNavigation: (NavBarVmItem) -> Unit,
) : NavBarVm {

  init {
    require(initialItems.isNotEmpty())
  }

  private val _items: MutableStateFlow<List<NavBarItemVmImpl>> = MutableStateFlow(initialItems.mapIndexed(::NavBarItemVmImpl))

  private val _selectedIndex: MutableStateFlow<Int> = MutableStateFlow(-1)

  private val _popupRequests: MutableSharedFlow<Int> = MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

  private val _popup: MutableStateFlow<NavBarPopupVmImpl?> = MutableStateFlow(null)

  init {
    cs.launch {
      contextItems.collectLatest { items ->
        if (items.isNotEmpty()) {
          _items.value = items.mapIndexed(::NavBarItemVmImpl)
          _selectedIndex.value = -1
          check(_popupRequests.tryEmit(-1))
          _popup.value = null
        }
      }
    }
    cs.launch {
      handleSelectionChange()
    }
    cs.launch {
      handlePopupRequests()
    }
  }

  override val items: StateFlow<List<NavBarItemVm>> = _items.asStateFlow()

  override val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

  override val popup: StateFlow<NavBarPopupVmImpl?> = _popup.asStateFlow()

  override fun selection(): List<Pointer<out NavBarItem>> {
    EDT.assertIsEdt()
    val popup = _popup.value
    if (popup != null) {
      return popup.selectedItems.map { it.pointer }
    }
    else {
      val selectedIndex = _selectedIndex.value
      val items = _items.value
      if (selectedIndex in items.indices) {
        return listOf(items[selectedIndex].item.pointer)
      }
    }
    return emptyList()
  }

  override fun shiftSelectionTo(shift: SelectionShift) {
    when (shift) {
      SelectionShift.FIRST -> _selectedIndex.value = 0
      SelectionShift.PREV -> _selectedIndex.update { (it - 1 + items.value.size).mod(items.value.size) }
      SelectionShift.NEXT -> _selectedIndex.update { (it + 1 + items.value.size).mod(items.value.size) }
      SelectionShift.LAST -> _selectedIndex.value = items.value.lastIndex
    }
  }

  override fun selectTail() {
    val items = items.value
    val i = (items.size - 2).coerceAtLeast(0)
    items[i].select()
  }

  override fun showPopup() {
    check(_popupRequests.tryEmit(_selectedIndex.value))
  }

  private suspend fun handleSelectionChange() {
    _items.collectLatest { items: List<NavBarItemVmImpl> ->
      _selectedIndex.zipWithNext { unselected, selected ->
        if (unselected >= 0) {
          items[unselected].selected.value = false
        }
        if (selected >= 0) {
          items[selected].selected.value = true
        }
        if (_popup.value != null) {
          check(_popupRequests.tryEmit(selected))
        }
      }.collect()
    }
  }

  private suspend fun handlePopupRequests() {
    _popupRequests.collectLatest {
      handlePopupRequest(it)
    }
  }

  private suspend fun handlePopupRequest(index: Int) {
    _popup.value = null // hide previous popup
    if (index < 0) {
      return
    }
    val items = _items.value
    val children = items[index].item.childItems() ?: return
    if (children.isEmpty()) {
      return
    }
    val nextItem = items.getOrNull(index + 1)?.item
    popupLoop(items.slice(0..index).map { it.item }, NavBarPopupVmImpl(
      items = children,
      initialSelectedItemIndex = children.indexOf(nextItem).coerceAtLeast(0),
    ))
  }

  private suspend fun popupLoop(items: List<NavBarVmItem>, popup: NavBarPopupVmImpl) {
    _popup.value = popup
    val selectedChild = try {
      popup.result.await()
    }
    catch (ce: CancellationException) {
      _popup.value = null
      throw ce
    }
    val expandResult = autoExpand(selectedChild)
                       ?: return
    when (expandResult) {
      is ExpandResult.NavigateTo -> {
        requestNavigation(expandResult.target)
        return
      }
      is ExpandResult.NextPopup -> {
        val newItems = items + expandResult.expanded
        val lastIndex = newItems.indices.last
        val newItemVms = newItems.mapIndexed(::NavBarItemVmImpl).also {
          it[lastIndex].selected.value = true
        }
        _items.value = newItemVms
        _selectedIndex.value = lastIndex
        popupLoop(newItems, NavBarPopupVmImpl(
          items = expandResult.children,
          initialSelectedItemIndex = 0,
        ))
      }
    }
  }

  private inner class NavBarItemVmImpl(
    val index: Int,
    val item: NavBarVmItem,
  ) : NavBarItemVm {

    override val presentation: NavBarItemPresentation get() = item.presentation

    override val isModuleContentRoot: Boolean get() = item.isModuleContentRoot

    override val isFirst: Boolean get() = index == 0

    override val isLast: Boolean get() = index == items.value.lastIndex

    override val selected: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override fun isNextSelected(): Boolean {
      return index == _selectedIndex.value - 1
    }

    override fun isInactive(): Boolean {
      return _selectedIndex.value != -1 && _selectedIndex.value < index
    }

    override fun select() {
      _selectedIndex.value = index
    }

    override fun showPopup() {
      this@NavBarVmImpl.showPopup()
    }

    override fun activate() {
      requestNavigation(item)
    }
  }
}

private sealed interface ExpandResult {
  class NavigateTo(val target: NavBarVmItem) : ExpandResult
  class NextPopup(val expanded: List<NavBarVmItem>, val children: List<NavBarVmItem>) : ExpandResult
}

private suspend fun autoExpand(child: NavBarVmItem): ExpandResult? {
  var expanded = emptyList<NavBarVmItem>()
  var currentItem = child
  var (children, navigateOnClick) = currentItem.childItemsAndNavigation() ?: return null

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
          val fetch = currentItem.childItemsAndNavigation() ?: return null
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

private suspend fun NavBarVmItem.childItems(): List<NavBarVmItem>? {
  return fetch {
    childItems()
  }
}

private suspend fun NavBarVmItem.childItemsAndNavigation(): Pair<List<NavBarVmItem>, Boolean>? {
  return fetch {
    Pair(childItems(), navigateOnClick())
  }
}

private fun NavBarItem.childItems(): List<NavBarVmItem> {
  return children().toVmItems()
}
