// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.*
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.util.concurrent.ConcurrentHashMap

internal class ToolboxSettingsActionRegistryState: BaseState() {
  val knownActions by list<String>()
}

@Service(Service.Level.APP)
@State(name = "toolbox-update-state", storages = [Storage(StoragePathMacros.CACHE_FILE)], allowLoadInTests = true)
internal class ToolboxSettingsActionRegistry : SimplePersistentStateComponent<ToolboxSettingsActionRegistryState>(ToolboxSettingsActionRegistryState()), Disposable {
  private val pendingActions : MutableMap<String, AnAction> = ConcurrentHashMap()

  private val alarm = MergingUpdateQueue("toolbox-updates", 500, true, null, this, null, Alarm.ThreadToUse.POOLED_THREAD).usePassThroughInUnitTestMode()

  override fun dispose() = Unit

  fun scheduleUpdate() {
    alarm.queue(object: Update(this){
      override fun run() {
        //we would not like it to overflow
        if (state.knownActions.size > 300) {
          val tail = state.knownActions.toList().takeLast(30).toHashSet()
          state.knownActions.clear()
          state.knownActions.addAll(tail)
          state.intIncrementModificationCount()
        }

        val ids = pendingActions.keys.toSortedSet()
        val iconState = if (!state.knownActions.containsAll(ids)) {
          state.knownActions.addAll(ids)
          state.intIncrementModificationCount()

          SettingsEntryPointAction.IconState.ApplicationUpdate
        } else {
          SettingsEntryPointAction.IconState.Current
        }

        invokeLater {
          SettingsEntryPointAction.updateState(iconState)
        }
      }
    })
  }

  fun registerUpdateAction(lifetime: Disposable, persistentActionId: String, action: AnAction) {
    val dispose = Disposable {
      pendingActions.remove(persistentActionId, action)
      scheduleUpdate()
    }

    pendingActions[persistentActionId] = action
    if (!Disposer.tryRegister(lifetime, dispose)) {
      Disposer.dispose(dispose)
      return
    }

    scheduleUpdate()
  }

  fun getActions() : List<AnAction> = pendingActions.entries.sortedBy { it.key }.map { it.value }
}

class ToolboxSettingsActionRegistryActionProvider : SettingsEntryPointAction.ActionProvider {
  override fun getUpdateActions(context: DataContext) = service<ToolboxSettingsActionRegistry>().getActions()
}
