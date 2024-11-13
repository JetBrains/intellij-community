// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import com.intellij.collaboration.async.collectScoped
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
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Cell
import com.intellij.util.ui.showingScope
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import com.intellij.vcs.ui.ProgressStripe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.*
import javax.swing.border.Border
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.text.JTextComponent
import kotlin.coroutines.CoroutineContext

/**
 * Binds the state of the combo box model with the given items state and selection flows.
 */
fun <T : Any> MutableCollectionComboBoxModel<T>.bindIn(
  scope: CoroutineScope,
  items: Flow<Collection<T>>,
  selectionState: MutableStateFlow<T?>,
  sortComparator: Comparator<T>,
) {
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

internal fun <T> ComboBoxWithActionsModel<T>.bindIn(
  scope: CoroutineScope,
  items: Flow<Collection<T>>,
  selectionState: MutableStateFlow<T?>,
  sortComparator: Comparator<T>,
) {
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

internal fun <T> ComboBoxWithActionsModel<T>.bindIn(
  scope: CoroutineScope,
  items: Flow<Collection<T>>,
  selectionState: MutableStateFlow<T?>,
  actions: Flow<List<Action>>,
  sortComparator: Comparator<T>,
) {
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

@ApiStatus.Internal
fun JComponent.bindDisabled(debugName: String, disabledFlow: Flow<Boolean>) {
  showingScope(debugName) {
    disabledFlow.collect {
      isEnabled = !it
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

fun JComponent.bindBorderIn(scope: CoroutineScope, borderFlow: Flow<Border>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    borderFlow.collect {
      border = it
    }
  }
}

fun JComponent.bindBackgroundIn(scope: CoroutineScope, backgroundFlow: Flow<Color>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    backgroundFlow.collect {
      background = it
    }
  }
}

fun JComponent.bindTooltipTextIn(scope: CoroutineScope, tooltipTextFlow: Flow<@Nls String>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    tooltipTextFlow.collect {
      toolTipText = it
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
      // JDK bug JBR-2256 - need to force height recalculation
      setSize(Int.MAX_VALUE / 2, Int.MAX_VALUE / 2)
    }
  }
}

@ApiStatus.Internal
fun JEditorPane.bindTextHtml(debugName: String, textFlow: Flow<@Nls String>) {
  showingScope(debugName) {
    textFlow.collect {
      setHtmlBody(it)
    }
  }
}

fun JEditorPane.bindTextHtmlIn(scope: CoroutineScope, textFlow: Flow<@Nls String>) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    textFlow.collect {
      setHtmlBody(it)
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

@ApiStatus.Internal
fun JLabel.bindIcon(debugName: String, iconFlow: Flow<Icon?>) {
  showingScope(debugName) {
    iconFlow.collect {
      icon = it
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
  bindTextIn(cs, textFlow) {
    textFlow.value = it
  }
}

fun Document.bindTextIn(cs: CoroutineScope, textFlow: StateFlow<String>, setter: (String) -> Unit) {
  val listener = object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      setter(text)
    }
  }
  cs.launchNow(CoroutineName("Text binding for $this")) {
    textFlow.collectScoped { newText ->
      if (text != newText) {
        writeAction {
          setText(newText.filter { it != '\r' })
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

@ApiStatus.Internal
fun <D> Wrapper.bindContent(
  debugName: String,
  dataFlow: Flow<D>,
  componentFactory: CoroutineScope.(D) -> JComponent?,
) {
  showingScope(debugName) {
    bindContentImpl(dataFlow, componentFactory)
  }
}

fun <D> Wrapper.bindContentIn(
  scope: CoroutineScope, dataFlow: Flow<D>,
  componentFactory: CoroutineScope.(D) -> JComponent?,
) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    bindContentImpl(dataFlow, componentFactory)
  }
}

@ApiStatus.Internal
fun Wrapper.bindContent(
  debugName: String, contentFlow: Flow<JComponent?>,
) {
  showingScope(debugName) {
    contentFlow.collect {
      setContent(it)
      repaint()
    }
  }
}

private suspend fun <D> Wrapper.bindContentImpl(
  dataFlow: Flow<D>,
  componentFactory: CoroutineScope.(D) -> JComponent?,
) {
  dataFlow.collectScoped {
    val component = componentFactory(it) ?: return@collectScoped
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

fun <D> JPanel.bindChildIn(
  scope: CoroutineScope, dataFlow: Flow<D>,
  constraints: Any? = null, index: Int? = null,
  componentFactory: CoroutineScope.(D) -> JComponent?,
) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    dataFlow.collectScoped {
      val component = componentFactory(it) ?: return@collectScoped
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

fun JCheckBox.bindSelectedIn(scope: CoroutineScope, flow: MutableStateFlow<Boolean>) {
  scope.launchNow {
    val listener = { _: Any? -> flow.value = model.isSelected }
    model.addChangeListener(listener)
    listener(null)
    try {
      flow.collect {
        model.isSelected = it
      }
    }
    finally {
      model.removeChangeListener(listener)
    }
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

@ApiStatus.Internal
@Deprecated("It is much better to pass a proper scope where needed")
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
    scope = GlobalScope.childScope("ActivatableCoroutineScopeProvider", context(), true).apply {
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