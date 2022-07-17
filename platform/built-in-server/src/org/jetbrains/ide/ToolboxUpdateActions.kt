// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.Consumer
import com.intellij.util.messages.Topic
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.annotations.Nls
import java.util.*

@Service(Service.Level.APP)
class ToolboxSettingsActionRegistry : Disposable {
  private val readActions = Collections.synchronizedSet(HashSet<String>())
  private val pendingActions = Collections.synchronizedList(LinkedList<ToolboxUpdateAction>())

  private val alarm = MergingUpdateQueue("toolbox-updates", 500, true, null, this, null, Alarm.ThreadToUse.SWING_THREAD).usePassThroughInUnitTestMode()

  override fun dispose() = Unit

  fun isNewAction(actionId: String) = actionId !in readActions

  fun markAsRead(actionId: String) {
    readActions.add(actionId)
  }

  fun scheduleUpdate() {
    alarm.queue(object: Update(this){
      override fun run() {
        SettingsEntryPointAction.updateState()
      }
    })
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

internal class ToolboxUpdateAction(
  val actionId: String,
  val lifetime: Disposable,
  text: @Nls String,
  description: @Nls String,
  val actionHandler: Consumer<AnActionEvent>,
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
    actionHandler.consume(e)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (Disposer.isDisposed(lifetime)) {
      e.presentation.isEnabledAndVisible = false
    }
  }
}

interface UpdateActionsListener: EventListener {
  companion object {
    val TOPIC = Topic(UpdateActionsListener::class.java)
  }

  fun actionReceived(action: SettingsEntryPointAction.UpdateAction)
}