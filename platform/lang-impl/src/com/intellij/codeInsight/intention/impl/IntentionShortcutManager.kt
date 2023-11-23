// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionBean
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInsight.intention.IntentionShortcuts.WRAPPER_PREFIX
import com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper
import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Wraps intentions in [IntentionActionAsAction] to make it possible to assign shortcuts to them */
@Service(Service.Level.APP)
class IntentionShortcutManager(private val coroutineScope: CoroutineScope) : Disposable {
  private var registeredKeymap: Keymap? = null

  /** Check if intention has a shortcut assigned */
  fun hasShortcut(intention: IntentionAction): Boolean = getShortcutSet(intention)?.shortcuts?.isNotEmpty() == true

  /** Get possible shortcuts assigned to intention */
  fun getShortcutSet(intention: IntentionAction): ShortcutSet? = findIntentionWrapper(ActionManager.getInstance(), intention)?.shortcutSet

  /** Remove the first shortcuts assigned to intention and unregister the wrapping [IntentionActionAsAction] if no shortcuts remain */
  fun removeFirstIntentionShortcut(intention: IntentionAction) {
    val shortcuts = getShortcutSet(intention)?.shortcuts ?: return

    shortcuts.firstOrNull()?.let {
      KeymapManager.getInstance()?.activeKeymap?.removeShortcut(intention.wrappedActionId, it)
    }

    if (shortcuts.size <= 1) {
      ActionManager.getInstance().unregister(intention)
    }
  }

  /** Show the keyboard shortcut panel to assign a shortcut */
  internal fun promptForIntentionShortcut(intention: IntentionAction, project: Project) {
    register(ActionManager.getInstance(), intention)

    val activeKeymap = KeymapManager.getInstance()?.activeKeymap ?: return
    coroutineScope.launch(Dispatchers.EDT) {
      val window = serviceAsync<WindowManager>().suggestParentWindow(project) ?: return@launch
      val actionId = intention.wrappedActionId
      val action = serviceAsync<ActionShortcutRestrictions>().getForActionId(actionId)
      KeymapPanel.addKeyboardShortcut(actionId, action, activeKeymap, window)
    }
  }

  private fun register(actionManager: ActionManager, intention: IntentionAction) {
    if (findIntentionWrapper(actionManager, intention) == null) {
      actionManager.registerAction(intention.wrappedActionId, IntentionActionAsAction(intention))
    }
  }

  private fun ActionManager.unregister(intention: IntentionAction) {
    unregisterAction(intention.wrappedActionId)
  }

  private fun findIntentionWrapper(actionManager: ActionManager, intention: IntentionAction): AnAction? {
    return actionManager.getAction(intention.wrappedActionId)
  }

  internal fun findIntention(wrappedActionId: String): IntentionAction? {
    return IntentionManager.getInstance().availableIntentions.firstOrNull { it.wrappedActionId == wrappedActionId }
  }

  /** Register all intentions with shortcuts and keep them up to date */
  private fun scheduleRegisterIntentionsInActiveKeymap(actionManager: ActionManager) {
    coroutineScope.launch {
      registerIntentionsInKeymap(actionManager, serviceAsync<KeymapManager>().activeKeymap)

      val connection = ApplicationManager.getApplication().messageBus.connect(coroutineScope)
      connection.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
        override fun activeKeymapChanged(keymap: Keymap?) {
          unregisterIntentionsInKeymap(actionManager)
          registerIntentionsInKeymap(actionManager, keymap)
        }
      })

      IntentionManagerImpl.EP_INTENTION_ACTIONS.addExtensionPointListener(coroutineScope, object : ExtensionPointListener<IntentionActionBean> {
        override fun extensionAdded(extension: IntentionActionBean, pluginDescriptor: PluginDescriptor) {
          registerIntentionsInKeymap(actionManager, registeredKeymap)
        }

        override fun extensionRemoved(extension: IntentionActionBean, pluginDescriptor: PluginDescriptor) {
          unregisterIntentionsInExtension(actionManager, extension)
        }
      })
    }
  }

  private fun registerIntentionsInKeymap(actionManager: ActionManager, keymap: Keymap?) {
    keymap?.let {
      for (action in wrappedIntentionActions(keymap)) {
        register(actionManager, action)
      }
    }
    registeredKeymap = keymap
  }

  private fun unregisterIntentionsInKeymap(actionManager: ActionManager) {
    for (it in wrappedIntentionActions(registeredKeymap ?: return)) {
      actionManager.unregister(it)
    }
    registeredKeymap = null
  }

  private fun unregisterIntentionsInExtension(actionManager: ActionManager, extension: IntentionActionBean) {
    for (intention in wrappedIntentionActions(registeredKeymap ?: return)) {
      if (intention is IntentionActionWrapper && intention.implementationClassName == extension.className) {
        actionManager.unregister(intention)
      }
    }
  }

  /** Collect all intentions with assigned shortcuts in a keymap */
  private fun wrappedIntentionActions(keymap: Keymap): Sequence<IntentionAction> {
    val intentionsById = IntentionManager.getInstance().intentionActions.associateBy { it.wrappedActionId }
    return keymap.actionIdList
      .asSequence()
      .filter { it.startsWith(WRAPPER_PREFIX) }
      .mapNotNull { intentionsById[it] }
  }

  override fun dispose() {}

  internal class InitListener : ActionConfigurationCustomizer {
    override fun customize(actionManager: ActionManager) {
      getInstance().scheduleRegisterIntentionsInActiveKeymap(actionManager)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): IntentionShortcutManager = service()
  }
}

