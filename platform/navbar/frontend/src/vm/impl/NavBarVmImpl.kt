// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend.vm.impl

import com.intellij.platform.navbar.NavBarItemExpandResultData
import com.intellij.platform.navbar.NavBarItemPresentationData
import com.intellij.platform.navbar.NavBarVmItem
import com.intellij.platform.navbar.frontend.vm.NavBarItemVm
import com.intellij.platform.navbar.frontend.vm.NavBarPopupVm
import com.intellij.platform.navbar.frontend.vm.NavBarVm
import com.intellij.platform.navbar.frontend.vm.NavBarVm.SelectionShift
import com.intellij.platform.util.coroutines.flow.zipWithNext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NavBarVmImpl(
  cs: CoroutineScope,
  initialItems: List<NavBarVmItem>,
  contextItems: Flow<List<NavBarVmItem>>,
) : NavBarVm {

  init {
    require(initialItems.isNotEmpty())
  }

  private val _items: MutableStateFlow<List<NavBarItemVmImpl>> = MutableStateFlow(initialItems.mapIndexed(::NavBarItemVmImpl))

  private val _selectedIndex: MutableStateFlow<Int> = MutableStateFlow(-1)

  private val _popupRequests: MutableSharedFlow<Int> = MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

  private val _popup: MutableStateFlow<NavBarPopupVmImpl<DefaultNavBarPopupItem>?> = MutableStateFlow(null)

  private val _activationRequests: MutableSharedFlow<NavBarVmItem> =
    MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

  init {
    cs.launch {
      contextItems.distinctUntilChanged().collectLatest { items ->
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

  override val popup: StateFlow<NavBarPopupVm<*>?> = _popup.asStateFlow()

  override val activationRequests: Flow<NavBarVmItem> = _activationRequests.asSharedFlow()

  override fun selection(): List<NavBarVmItem> {
    val popup = _popup.value
    if (popup != null) {
      return popup.selectedItems.map {
        it.item
      }
    }
    else {
      val selectedIndex = _selectedIndex.value
      val items = _items.value
      if (selectedIndex in items.indices) {
        return listOf(items[selectedIndex].item)
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

  override fun selectTail(withPopupOpen: Boolean) {
    val shiftToLeft = if (withPopupOpen) 1 else 0

    val items = items.value
    val i = (items.size - 1 - shiftToLeft).coerceAtLeast(0)
    items[i].select()

    if (withPopupOpen) showPopup()
  }

  override fun showPopup() {
    check(_popupRequests.tryEmit(_selectedIndex.value))
  }

  private suspend fun handleSelectionChange() {
    _items.collectLatest { items: List<NavBarItemVmImpl> ->
      _selectedIndex.zipWithNext { unselected, selected ->

        // Sometimes _selectedIndex may come here before the new _items value even if the _items value was set before _selectedIndex value.
        // This check prevents OutOfBounds exception.
        // The _selectedIndex will be replayed here again as soon as the new _items value is collected.
        if (selected >= items.size) {
          return@zipWithNext
        }

        if (unselected >= 0 && unselected < items.size) {
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
    val children = items[index].item.children() ?: return
    if (children.isEmpty()) {
      return
    }
    val nextItem = items.getOrNull(index + 1)?.item
    popupLoop(items.slice(0..index).map { it.item }, NavBarPopupVmImpl(
      items = children.map(::DefaultNavBarPopupItem),
      initialSelectedItemIndex = children.indexOf(nextItem).coerceAtLeast(0),
    ))
  }

  private suspend fun popupLoop(items: List<NavBarVmItem>, popup: NavBarPopupVmImpl<DefaultNavBarPopupItem>) {
    _popup.value = popup
    val selectedChild = try {
      popup.result.await().item
    }
    catch (ce: CancellationException) {
      _popup.value = null
      throw ce
    }
    val expandResult = autoExpand(selectedChild)
                       ?: return
    when (expandResult) {
      is ExpandResult.NavigateTo -> {
        _activationRequests.tryEmit(expandResult.target)
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
          items = expandResult.children.map(::DefaultNavBarPopupItem),
          initialSelectedItemIndex = 0,
        ))
      }
    }
  }

  private inner class NavBarItemVmImpl(
    val index: Int,
    val item: NavBarVmItem,
  ) : NavBarItemVm {

    override val presentation: NavBarItemPresentationData get() = item.presentation as NavBarItemPresentationData

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
      _activationRequests.tryEmit(item)
    }

    override fun toString(): String {
      return item.toString()
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
  var (children, navigateOnClick) = currentItem.expand() as NavBarItemExpandResultData? ?: return null

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
          val fetch = currentItem.expand() as NavBarItemExpandResultData? ?: return null
          children = fetch.children
          navigateOnClick = fetch.navigateOnClick
        }
      }
      else -> {
        // [currentItem] has several children, so return it with current [expanded] trace.
        return ExpandResult.NextPopup(expanded + currentItem, children)
      }
    }
  }
}
