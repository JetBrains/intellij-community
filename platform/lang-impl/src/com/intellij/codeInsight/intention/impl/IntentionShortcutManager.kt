// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionBean
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInsight.intention.IntentionShortcuts.WRAPPER_PREFIX
import com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper
import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
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
import org.jetbrains.annotations.ApiStatus

/** Wraps intentions in [IntentionActionAsAction] to make it possible to assign shortcuts to them */
@ApiStatus.Internal
@Service(Service.Level.APP)
class IntentionShortcutManager(private val coroutineScope: CoroutineScope) {
  private var registeredKeymap: Keymap? = null

  /** Check if intention has a shortcut assigned */
  fun hasShortcut(intention: IntentionAction): Boolean = getShortcutSet(intention)?.shortcuts?.isNotEmpty() == true

  /** Get possible shortcuts assigned to intention */
  fun getShortcutSet(intention: IntentionAction): ShortcutSet? {
    return findIntentionWrapper(ActionManagerEx.getInstanceEx().asActionRuntimeRegistrar(), intention)?.shortcutSet
  }

  /** Remove the first shortcuts assigned to intention and unregister the wrapping [IntentionActionAsAction] if no shortcuts remain */
  fun removeFirstIntentionShortcut(intention: IntentionAction) {
    val shortcuts = getShortcutSet(intention)?.shortcuts ?: return

    shortcuts.firstOrNull()?.let {
      KeymapManager.getInstance()?.activeKeymap?.removeShortcut(intention.wrappedActionId, it)
    }

    if (shortcuts.size <= 1) {
      unregister(intention, ActionManagerEx.getInstanceEx().asActionRuntimeRegistrar())
    }
  }

  /** Show the keyboard shortcut panel to assign a shortcut */
  internal fun promptForIntentionShortcut(intention: IntentionAction, project: Project) {
    register(ActionManagerEx.getInstanceEx().asActionRuntimeRegistrar(), intention)

    val activeKeymap = KeymapManager.getInstance()?.activeKeymap ?: return
    coroutineScope.launch(Dispatchers.EDT) {
      val window = serviceAsync<WindowManager>().suggestParentWindow(project) ?: return@launch
      val actionId = intention.wrappedActionId
      val action = serviceAsync<ActionShortcutRestrictions>().getForActionId(actionId)
      KeymapPanel.addKeyboardShortcut(actionId, action, activeKeymap, window)
    }
  }

  private fun register(actionRegistrar: ActionRuntimeRegistrar, intention: IntentionAction) {
    if (findIntentionWrapper(actionRegistrar, intention) == null) {
      actionRegistrar.registerAction(intention.wrappedActionId, IntentionActionAsAction(intention))
    }
  }

  private fun unregister(intention: IntentionAction, actionRegistrar: ActionRuntimeRegistrar) {
    actionRegistrar.unregisterAction(intention.wrappedActionId)
  }

  private fun findIntentionWrapper(actionRegistrar: ActionRuntimeRegistrar, intention: IntentionAction): AnAction? {
    return actionRegistrar.getActionOrStub(intention.wrappedActionId)
  }

  internal fun findIntention(wrappedActionId: String): IntentionAction? {
    return IntentionManager.getInstance().availableIntentions.firstOrNull { it.wrappedActionId == wrappedActionId }
  }

  // register all intentions with shortcuts and keep them up to date
  private fun scheduleRegisterIntentionsInActiveKeymap(actionRegistrar: ActionRuntimeRegistrar) {
    coroutineScope.launch {
      registerIntentionsInKeymap(actionRegistrar, serviceAsync<KeymapManager>().activeKeymap)

      val connection = ApplicationManager.getApplication().messageBus.connect(coroutineScope)
      connection.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
        override fun activeKeymapChanged(keymap: Keymap?) {
          unregisterIntentionsInKeymap(actionRegistrar)
          registerIntentionsInKeymap(actionRegistrar, keymap)
        }
      })

      IntentionManagerImpl.EP_INTENTION_ACTIONS.addExtensionPointListener(coroutineScope, object : ExtensionPointListener<IntentionActionBean> {
        override fun extensionAdded(extension: IntentionActionBean, pluginDescriptor: PluginDescriptor) {
          registerIntentionsInKeymap(actionRegistrar, registeredKeymap)
        }

        override fun extensionRemoved(extension: IntentionActionBean, pluginDescriptor: PluginDescriptor) {
          unregisterIntentionsInExtension(actionRegistrar, extension)
        }
      })
    }
  }

  private fun registerIntentionsInKeymap(actionRegistrar: ActionRuntimeRegistrar, keymap: Keymap?) {
    keymap?.let {
      for (action in wrappedIntentionActions(keymap)) {
        register(actionRegistrar, action)
      }
    }
    registeredKeymap = keymap
  }

  private fun unregisterIntentionsInKeymap(actionRegistrar: ActionRuntimeRegistrar) {
    for (it in wrappedIntentionActions(registeredKeymap ?: return)) {
      unregister(it, actionRegistrar)
    }
    registeredKeymap = null
  }

  private fun unregisterIntentionsInExtension(actionRegistrar: ActionRuntimeRegistrar, extension: IntentionActionBean) {
    for (intention in wrappedIntentionActions(registeredKeymap ?: return)) {
      if (intention is IntentionActionWrapper && intention.implementationClassName == extension.className) {
        unregister(intention, actionRegistrar)
      }
    }
  }

  // collect all intentions with assigned shortcuts in a keymap
  private fun wrappedIntentionActions(keymap: Keymap): Sequence<IntentionAction> {
    val intentionsById = IntentionManager.getInstance().intentionActions.associateBy { it.wrappedActionId }
    return keymap.actionIdList
      .asSequence()
      .filter { it.startsWith(WRAPPER_PREFIX) }
      .mapNotNull { intentionsById.get(it) }
  }

  internal class InitListener : ActionConfigurationCustomizer, ActionConfigurationCustomizer.AsyncLightCustomizeStrategy {
    override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
      serviceAsync<IntentionShortcutManager>().scheduleRegisterIntentionsInActiveKeymap(actionRegistrar)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): IntentionShortcutManager = service()
  }
}

