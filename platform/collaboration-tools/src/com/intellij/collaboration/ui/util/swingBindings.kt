// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import com.intellij.collaboration.ui.ComboBoxWithActionsModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import javax.swing.Action
import javax.swing.ComboBoxModel
import javax.swing.JComponent
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.text.JTextComponent

//TODO: generalise
fun <T : Any> ComboBoxWithActionsModel<T>.bind(scope: CoroutineScope,
                                               itemsState: StateFlow<Collection<T>>,
                                               selectionState: MutableStateFlow<T?>,
                                               sortComparator: Comparator<T>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    itemsState.collect {
      items = it.sortedWith(sortComparator)
    }
  }
  addSelectionChangeListener(scope) {
    selectionState.value = selectedItem?.wrappee
  }
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    selectionState.collect { item ->
      if (selectedItem?.wrappee != item) {
        selectedItem = item?.let { ComboBoxWithActionsModel.Item.Wrapper(it) }
      }
    }
  }
}

//TODO: generalise
fun <T : Any> ComboBoxWithActionsModel<T>.bind(scope: CoroutineScope,
                                               itemsState: StateFlow<Collection<T>>,
                                               selectionState: MutableStateFlow<T?>,
                                               actionsState: StateFlow<List<Action>>,
                                               sortComparator: Comparator<T>) {
  bind(scope, itemsState, selectionState, sortComparator)

  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    actionsState.collect {
      actions = it
    }
  }
}

private fun <T> ComboBoxModel<T>.addSelectionChangeListener(scope: CoroutineScope, listener: () -> Unit) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    val dataListener = object : ListDataListener {
      override fun contentsChanged(e: ListDataEvent) {
        if (e.index0 == -1 && e.index1 == -1) listener()
      }

      override fun intervalAdded(e: ListDataEvent) {}
      override fun intervalRemoved(e: ListDataEvent) {}
    }
    try {
      addListDataListener(dataListener)
      awaitCancellation()
    } finally {
      removeListDataListener(dataListener)
    }
  }
}

fun JComponent.bindVisibility(scope: CoroutineScope, visibilityFlow: Flow<Boolean>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    visibilityFlow.collect {
      isVisible = it
    }
  }
}

fun JComponent.bindDisabled(scope: CoroutineScope, disabledFlow: Flow<Boolean>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    disabledFlow.collect {
      isEnabled = !it
    }
  }
}

fun JTextComponent.bindText(scope: CoroutineScope, textFlow: Flow<@Nls String>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    textFlow.collect {
      text = it
    }
  }
}