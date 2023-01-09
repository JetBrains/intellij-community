// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.util.function.Consumer
import java.util.function.Function
import javax.swing.KeyStroke

@Suppress("DeprecatedCallableAddReplaceWith")
abstract class ActionManagerEx : ActionManager() {
  companion object {
    @JvmStatic
    fun getInstanceEx(): ActionManagerEx = getInstance() as ActionManagerEx

    /**
     * Similar to [KeyStroke.getKeyStroke] but allows keys in lower case.
     *
     * I.e. "control x" is accepted and interpreted as "control X".
     *
     * @return null if string cannot be parsed.
     */
    @JvmStatic
    fun getKeyStroke(s: String): KeyStroke? {
      var result = try {
        KeyStroke.getKeyStroke(s)
      }
      catch (ignore: Exception) {
        null
      }

      if (result == null && s.length >= 2 && s[s.length - 2] == ' ') {
        try {
          val s1 = s.substring(0, s.length - 1) + s[s.length - 1].uppercaseChar()
          result = KeyStroke.getKeyStroke(s1)
        }
        catch (ignored: Exception) {
        }
      }
      return result
    }

    @ApiStatus.Internal
    @JvmStatic
    fun doWithLazyActionManager(whatToDo: Consumer<ActionManager>) {
      withLazyActionManager(scope = null, task = whatToDo::accept)
    }

    @ApiStatus.Internal
    inline fun withLazyActionManager(scope: CoroutineScope?, crossinline task: (ActionManager) -> Unit) {
      val app = ApplicationManager.getApplication()
      val created = app.serviceIfCreated<ActionManager>()
      if (created == null) {
        @Suppress("DEPRECATION")
        (scope ?: app.coroutineScope).launch {
          val actionManager = (app as ComponentManagerEx).getServiceAsync(ActionManager::class.java).await()
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

  abstract fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean, decorateButtons: Boolean): ActionToolbar

  abstract fun createActionToolbar(place: String,
                                   group: ActionGroup,
                                   horizontal: Boolean,
                                   separatorCreator: Function<in String, out Component>): ActionToolbar

  /**
   * Do not call directly, prefer [ActionUtil] methods.
   */
  @ApiStatus.Internal
  abstract fun fireBeforeActionPerformed(action: AnAction, event: AnActionEvent)

  /**
   * Do not call directly, prefer [ActionUtil] methods.
   */
  @ApiStatus.Internal
  abstract fun fireAfterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult)

  @Deprecated("use {@link #fireBeforeActionPerformed(AnAction, AnActionEvent)} instead")
  fun fireBeforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
    fireBeforeActionPerformed(action, event)
  }

  @Deprecated("use {@link #fireAfterActionPerformed(AnAction, AnActionEvent, AnActionResult)} instead")
  fun fireAfterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
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
}