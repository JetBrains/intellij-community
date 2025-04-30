// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.KeymapUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
import java.util.function.Function
import javax.swing.KeyStroke

@Suppress("DeprecatedCallableAddReplaceWith")
abstract class ActionManagerEx : ActionManager() {
  companion object {
    @JvmStatic
    fun getInstanceEx(): ActionManagerEx = getInstance() as ActionManagerEx

    @Deprecated("Use [KeymapUtil.getKeyStroke(s)]",
                ReplaceWith("KeymapUtil.getKeyStroke(s)"),
                DeprecationLevel.WARNING)
    @JvmStatic
    fun getKeyStroke(s: String): KeyStroke? = KeymapUtil.getKeyStroke(s)

    @Internal
    @JvmStatic
    fun withLazyActionManager(scope: CoroutineScope?, task: (ActionManager) -> Unit) {
      val app = ApplicationManager.getApplication()
      if (app == null || app.isDisposed) return
      val created = app.serviceIfCreated<ActionManager>()
      if (created == null) {
        (scope ?: (app as ComponentManagerEx).getCoroutineScope()).launch {
          val actionManager = app.serviceAsync<ActionManager>()
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            task(actionManager)
          }
        }
      }
      else {
        task(created)
      }
    }
  }

  abstract fun performWithActionCallbacks(action: AnAction, event: AnActionEvent, runnable: Runnable): AnActionResult

  abstract fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean, decorateButtons: Boolean): ActionToolbar

  abstract fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean, decorateButtons: Boolean, customizable: Boolean): ActionToolbar

  abstract fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean, separatorCreator: Function<in String, out Component>): ActionToolbar

  @Deprecated("Use [ActionUtil.performActionDumbAwareWithCallbacks] instead",
              ReplaceWith("ActionUtil.performActionDumbAwareWithCallbacks"),
              DeprecationLevel.WARNING)
  @Internal
  abstract fun fireBeforeActionPerformed(action: AnAction, event: AnActionEvent)

  @Deprecated("Use [ActionUtil.performActionDumbAwareWithCallbacks] instead",
              ReplaceWith("ActionUtil.performActionDumbAwareWithCallbacks"),
              DeprecationLevel.WARNING)
  @Internal
  abstract fun fireAfterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult)

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use [ActionUtil.performActionDumbAwareWithCallbacks] instead",
              ReplaceWith("ActionUtil.performActionDumbAwareWithCallbacks"),
              DeprecationLevel.ERROR)
  fun fireBeforeActionPerformed(action: AnAction, @Suppress("unused") dataContext: DataContext, event: AnActionEvent) {
    @Suppress("DEPRECATION")
    fireBeforeActionPerformed(action, event)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use [ActionUtil.performActionDumbAwareWithCallbacks] instead",
              ReplaceWith("ActionUtil.performActionDumbAwareWithCallbacks"),
              DeprecationLevel.ERROR)
  fun fireAfterActionPerformed(action: AnAction, @Suppress("unused") dataContext: DataContext, event: AnActionEvent) {
    @Suppress("DEPRECATION")
    fireAfterActionPerformed(action, event, AnActionResult.PERFORMED)
  }

  abstract fun fireBeforeEditorTyping(c: Char, dataContext: DataContext)

  abstract fun fireAfterEditorTyping(c: Char, dataContext: DataContext)

  /**
   * For logging purposes
   */
  abstract val lastPreformedActionId: String?

  abstract val prevPreformedActionId: String?

  /**
   * A comparator that compares action ids (String) by the order of action registration.
   *
   * @return a negative integer if the action that corresponds to the first id was registered earlier than the action that corresponds
   * to the second id; zero if both ids are equal; a positive number otherwise.
   */
  abstract val registrationOrderComparator: Comparator<String>

  abstract fun getPluginActions(pluginId: PluginId): Array<String>

  abstract val isActionPopupStackEmpty: Boolean

  /**
   * Allows receiving notifications when popup menus created from action groups are shown and hidden.
   */
  abstract fun addActionPopupMenuListener(listener: ActionPopupMenuListener, parentDisposable: Disposable)

  @get:Internal
  @get:ApiStatus.Experimental
  abstract val timerEvents: Flow<Unit>

  @Internal
  abstract fun getActionBinding(actionId: String): String?

  @Internal
  abstract fun getBoundActions(): Set<String>

  @Internal
  abstract fun bindShortcuts(sourceActionId: String, targetActionId: String)

  @Internal
  abstract fun unbindShortcuts(targetActionId: String)

  @Internal
  abstract fun asActionRuntimeRegistrar(): ActionRuntimeRegistrar
}

@Internal
@ApiStatus.Experimental
interface ActionRuntimeRegistrar {
  fun registerAction(actionId: String, action: AnAction)

  fun unregisterActionByIdPrefix(idPrefix: String)

  fun unregisterAction(actionId: String)

  // do not add API like `getAction` - `ActionRuntimeRegistrar` should not unstub actions
  fun getActionOrStub(actionId: String): AnAction?

  fun getUnstubbedAction(actionId: String): AnAction?

  fun addToGroup(group: AnAction, action: AnAction, constraints: Constraints)

  fun replaceAction(actionId: String, newAction: AnAction)

  fun getId(action: AnAction): String?

  fun getBaseAction(overridingAction: OverridingAction): AnAction?
}
