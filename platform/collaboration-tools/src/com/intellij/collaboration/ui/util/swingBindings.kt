// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.ComboBoxWithActionsModel
import com.intellij.collaboration.ui.setHtmlBody
import com.intellij.collaboration.ui.setItems
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.namedChildScope
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Cell
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import com.intellij.vcs.ui.ProgressStripe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.Nls
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.text.JTextComponent
import kotlin.coroutines.CoroutineContext

/**
 * Binds the state of the combo box model with the given items state and selection flows.
 */
fun <T : Any> MutableCollectionComboBoxModel<T>.bindIn(scope: CoroutineScope,
                                                       items: Flow<Collection<T>>,
                                                       selectionState: MutableStateFlow<T?>,
                                                       sortComparator: Comparator<T>) {
  scope.launchNow {
    items.collect {
      setItems(it.sortedWith(sortComparator))
    }
  }
  addSelectionChangeListenerIn(scope) {
    @Suppress("UNCHECKED_CAST")
    selectionState.value = selectedItem as? T
  }
  scope.launchNow {
    selectionState.collect { item ->
      if (selectedItem != item) {
        selectedItem = item
      }
    }
  }
}

fun <T> ComboBoxWithActionsModel<T>.bindIn(scope: CoroutineScope,
                                           items: Flow<Collection<T>>,
                                           selectionState: MutableStateFlow<T?>,
                                           sortComparator: Comparator<T>) {
  scope.launchNow {
    items.collect {
      this@bindIn.items = it.sortedWith(sortComparator)
    }
  }
  addSelectionChangeListenerIn(scope) {
    selectionState.value = selectedItem?.wrappee
  }
  scope.launchNow {
    selectionState.collect { item ->
      if (selectedItem?.wrappee != item) {
        selectedItem = item?.let { ComboBoxWithActionsModel.Item.Wrapper(it) }
      }
    }
  }
}

fun <T> ComboBoxWithActionsModel<T>.bindIn(scope: CoroutineScope,
                                           items: Flow<Collection<T>>,
                                           selectionState: MutableStateFlow<T?>,
                                           actions: Flow<List<Action>>,
                                           sortComparator: Comparator<T>) {
  bindIn(scope, items, selectionState, sortComparator)

  scope.launchNow {
    actions.collect {
      this@bindIn.actions = it
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

fun JComponent.bindEnabledIn(scope: CoroutineScope, enabledFlow: Flow<Boolean>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    enabledFlow.collect {
      isEnabled = it
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

fun Document.bindTextIn(cs: CoroutineScope, textFlow: MutableStateFlow<String>) {
  cs.launchNow(CoroutineName("Downstream text binding for $this")) {
    val listener = object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        textFlow.value = text
      }
    }
    addDocumentListener(listener)
    try {
      awaitCancellation()
    }
    finally {
      removeDocumentListener(listener)
    }
  }

  cs.launchNow(CoroutineName("Upstream text binding for $this")) {
    textFlow.collect {
      if (text != it) {
        writeAction {
          setText(it)
        }
      }
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

fun <T> JBList<T>.bindBusyIn(scope: CoroutineScope, busyFlow: Flow<Boolean>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    busyFlow.collect { isBusy ->
      setPaintBusy(isBusy)
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

fun JCheckBox.bindSelectedIn(scope: CoroutineScope, flow: MutableStateFlow<Boolean>) {
  val listener = { _: Any -> flow.value = model.isSelected }
  model.addChangeListener(listener)

  scope.launchNow {
    flow.collect {
      model.isSelected = it
    }
  }

  scope.awaitCancellationAndInvoke {
    model.removeChangeListener(listener)
  }
}

fun Cell<JCheckBox>.bindSelectedIn(scope: CoroutineScope, flow: MutableStateFlow<Boolean>) = applyToComponent {
  bindSelectedIn(scope, flow)
}

fun <T> ComboBoxModel<T>.bindSelectedItemIn(scope: CoroutineScope, flow: MutableStateFlow<T?>) {
  @Suppress("UNCHECKED_CAST")
  addSelectionChangeListenerIn(scope) { flow.value = (selectedItem as T?) }

  scope.launchNow {
    flow.collect {
      selectedItem = it
    }
  }
}

fun <T> Cell<ComboBox<T>>.bindSelectedItemIn(scope: CoroutineScope, flow: MutableStateFlow<T?>) = applyToComponent {
  model.bindSelectedItemIn(scope, flow)
}

private typealias Block = CoroutineScope.() -> Unit

class ActivatableCoroutineScopeProvider(private val context: () -> CoroutineContext = { Dispatchers.Main })
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

  @OptIn(DelicateCoroutinesApi::class)
  override fun showNotify() {
    scope = GlobalScope.namedChildScope("ActivatableCoroutineScopeProvider", context(), true).apply {
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