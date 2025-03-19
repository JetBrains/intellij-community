// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.Topic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.Nls
import java.util.*

@OptIn(FlowPreview::class)
@Service(Service.Level.APP)
class ToolboxSettingsActionRegistry(coroutineScope: CoroutineScope) {
  private val readActions = Collections.synchronizedSet(HashSet<String>())
  private val pendingActions = Collections.synchronizedList(LinkedList<ToolboxUpdateAction>())

  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch {
      updateRequests
        .debounce(500)
        .collectLatest {
          withContext(Dispatchers.EDT) {
            SettingsEntryPointAction.updateState()
          }
        }
    }
  }

  fun isNewAction(actionId: String) = actionId !in readActions

  fun markAsRead(actionId: String) {
    readActions.add(actionId)
  }

  fun scheduleUpdate() {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      SettingsEntryPointAction.updateState()
    }
    else {
      check(updateRequests.tryEmit(Unit))
    }
  }

  internal fun registerUpdateAction(action: ToolboxUpdateAction) {
    action.registry = this

    val dispose = Disposable {
      pendingActions.remove(action)
      scheduleUpdate()
    }

    pendingActions += action
    ApplicationManager.getApplication().messageBus.syncPublisher(UpdateActionsListener.TOPIC).actionReceived(action)

    if (!Disposer.tryRegister(action.lifetime, dispose)) {
      Disposer.dispose(dispose)
      return
    }

    scheduleUpdate()
  }

  fun getActions() : List<SettingsEntryPointAction.UpdateAction> = ArrayList(pendingActions).sortedBy { it.actionId }
}

class ToolboxSettingsActionRegistryActionProvider : SettingsEntryPointAction.ActionProvider {
  override fun getUpdateActions(context: DataContext) = service<ToolboxSettingsActionRegistry>().getActions()
}

class ToolboxUpdateAction(
  val actionId: String,
  val lifetime: Disposable,
  text: @Nls String,
  description: @Nls String,
  private val actionHandler: Runnable,
  val restartRequired: Boolean
) : SettingsEntryPointAction.UpdateAction(text) {
  lateinit var registry : ToolboxSettingsActionRegistry

  init {
    templatePresentation.description = description
  }

  override fun isIdeUpdate() = true

  override fun isRestartRequired(): Boolean {
    return restartRequired
  }

  override fun isNewAction(): Boolean {
    return registry.isNewAction(actionId)
  }

  override fun markAsRead() {
    registry.markAsRead(actionId)
  }

  override fun actionPerformed(e: AnActionEvent) {
    perform()
  }

  fun perform() {
    actionHandler.run()
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (Disposer.isDisposed(lifetime)) {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
}

interface UpdateActionsListener: EventListener {
  companion object {
    val TOPIC = Topic(UpdateActionsListener::class.java)
  }

  fun actionReceived(action: SettingsEntryPointAction.UpdateAction)
}