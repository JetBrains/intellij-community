// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import com.intellij.collaboration.ui.ComboBoxWithActionsModel
import com.intellij.collaboration.ui.setHtmlBody
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import com.intellij.vcs.log.ui.frame.ProgressStripe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.Nls
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.text.JTextComponent
import kotlin.coroutines.CoroutineContext

//TODO: generalise
fun <T : Any> ComboBoxWithActionsModel<T>.bindIn(scope: CoroutineScope,
                                                 itemsState: StateFlow<Collection<T>>,
                                                 selectionState: MutableStateFlow<T?>,
                                                 sortComparator: Comparator<T>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    itemsState.collect {
      items = it.sortedWith(sortComparator)
    }
  }
  addSelectionChangeListenerIn(scope) {
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
fun <T : Any> ComboBoxWithActionsModel<T>.bindIn(scope: CoroutineScope,
                                                 itemsState: StateFlow<Collection<T>>,
                                                 selectionState: MutableStateFlow<T?>,
                                                 actionsState: StateFlow<List<Action>>,
                                                 sortComparator: Comparator<T>) {
  bindIn(scope, itemsState, selectionState, sortComparator)

  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    actionsState.collect {
      actions = it
    }
  }
}

private fun <T> ComboBoxModel<T>.addSelectionChangeListenerIn(scope: CoroutineScope, listener: () -> Unit) {
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
    }
    finally {
      removeListDataListener(dataListener)
    }
  }
}

fun JComponent.bindVisibilityIn(scope: CoroutineScope, visibilityFlow: Flow<Boolean>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    visibilityFlow.collect {
      isVisible = it
    }
  }
}

fun JComponent.bindDisabledIn(scope: CoroutineScope, disabledFlow: Flow<Boolean>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    disabledFlow.collect {
      isEnabled = !it
    }
  }
}

fun JTextComponent.bindTextIn(scope: CoroutineScope, textFlow: Flow<@Nls String>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    textFlow.collect {
      text = it
    }
  }
}

fun JEditorPane.bindTextIn(scope: CoroutineScope, textFlow: Flow<@Nls String>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    textFlow.collect {
      text = it
      setSize(Int.MAX_VALUE / 2, Int.MAX_VALUE / 2)
    }
  }
}

fun JEditorPane.bindTextHtmlIn(scope: CoroutineScope, textFlow: Flow<@Nls String>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    textFlow.collect {
      setHtmlBody(it)
      setSize(Int.MAX_VALUE / 2, Int.MAX_VALUE / 2)
    }
  }
}

fun JLabel.bindTextIn(scope: CoroutineScope, textFlow: Flow<@Nls String>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    textFlow.collect {
      text = it
    }
  }
}

fun JLabel.bindIconIn(scope: CoroutineScope, iconFlow: Flow<Icon?>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    iconFlow.collect {
      icon = it
    }
  }
}

fun JButton.bindTextIn(scope: CoroutineScope, textFlow: Flow<@Nls String>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    textFlow.collect {
      text = it
    }
  }
}

fun Action.bindTextIn(scope: CoroutineScope, textFlow: Flow<@Nls String>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    textFlow.collect {
      putValue(Action.NAME, it)
    }
  }
}

fun Action.bindEnabledIn(scope: CoroutineScope, enabledFlow: Flow<Boolean>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    enabledFlow.collect {
      isEnabled = it
    }
  }
}

fun Wrapper.bindContentIn(scope: CoroutineScope, contentFlow: Flow<JComponent?>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    contentFlow.collect {
      setContent(it)
      repaint()
    }
  }
}

fun <D> Wrapper.bindContentIn(scope: CoroutineScope, dataFlow: Flow<D>,
                              componentFactory: CoroutineScope.(D) -> JComponent?) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    dataFlow.collectLatest {
      coroutineScope {
        val component = componentFactory(it) ?: return@coroutineScope
        setContent(component)
        repaint()

        try {
          awaitCancellation()
        }
        finally {
          setContent(null)
          repaint()
        }
      }
    }
  }
}

fun ProgressStripe.bindProgressIn(scope: CoroutineScope, loadingFlow: Flow<Boolean>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    loadingFlow.collect {
      if (it) startLoadingImmediately() else stopLoading()
    }
  }
}

fun <D> JPanel.bindChildIn(scope: CoroutineScope, dataFlow: Flow<D>,
                           constraints: Any? = null, index: Int? = null,
                           componentFactory: CoroutineScope.(D) -> JComponent?) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    dataFlow.collectLatest {
      coroutineScope {
        val component = componentFactory(it) ?: return@coroutineScope
        if (index != null) {
          add(component, constraints, index)
        }
        else {
          add(component, constraints)
        }
        validate()
        repaint()

        try {
          awaitCancellation()
        }
        finally {
          remove(component)
          revalidate()
          repaint()
        }
      }
    }
  }
}

private typealias Block = CoroutineScope.() -> Unit

class ActivatableCoroutineScopeProvider(private val context: () -> CoroutineContext = { SupervisorJob() + Dispatchers.Main })
  : Activatable {

  private var scope: CoroutineScope? = null
  private var blocks = mutableListOf<Block>()

  private var currentConnection: UiNotifyConnector? = null

  fun launchInScope(block: suspend CoroutineScope.() -> Unit) = doInScope {
    launch { block() }
  }

  fun doInScope(block: Block) {
    blocks.add(block)
    scope?.run {
      block()
    }
  }

  override fun showNotify() {
    scope = CoroutineScope(context()).apply {
      for (block in blocks) {
        launch { block() }
      }
    }
  }

  override fun hideNotify() {
    scope?.cancel()
    scope = null
  }

  fun activateWith(component: JComponent) {
    currentConnection?.let {
      Disposer.dispose(it)
    }
    currentConnection = UiNotifyConnector.installOn(component, this, false)
  }
}