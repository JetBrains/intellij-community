// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionBean
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInsight.intention.IntentionShortcuts.WRAPPER_PREFIX
import com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper
import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager

/** Wraps intentions in [IntentionActionAsAction] to make it possible to assign shortcuts to them */
@Service(Service.Level.APP)
class IntentionShortcutManager : Disposable {

  private var registeredKeymap: Keymap? = null

  /** Check if intention has a shortcut assigned */
  fun hasShortcut(intention: IntentionAction): Boolean =
    getShortcutSet(intention)?.shortcuts?.isNotEmpty() == true

  /** Get possible shortcuts assigned to intention */
  fun getShortcutSet(intention: IntentionAction): ShortcutSet? =
    findIntentionWrapper(intention)?.shortcutSet

  /** Remove the first shortcuts assigned to intention and unregister the wrapping [IntentionActionAsAction] if no shortcuts remain */
  fun removeFirstIntentionShortcut(intention: IntentionAction) {
    val shortcuts = getShortcutSet(intention)?.shortcuts ?: return

    shortcuts.firstOrNull()?.let {
      KeymapManager.getInstance()?.activeKeymap?.removeShortcut(intention.wrappedActionId, it)
    }

    if (shortcuts.size <= 1) {
      unregister(intention)
    }
  }

  /** Show the keyboard shortcut panel to assign a shortcut */
  fun promptForIntentionShortcut(intention: IntentionAction, project: Project) {

    register(intention)

    val km = KeymapManager.getInstance()
    val activeKeymap = km?.activeKeymap ?: return // not available

    ApplicationManager.getApplication().invokeLater {
      val window = WindowManager.getInstance().suggestParentWindow(project) ?: return@invokeLater
      val actionId = intention.wrappedActionId
      val action = ActionShortcutRestrictions.getInstance().getForActionId(actionId)
      KeymapPanel.addKeyboardShortcut(actionId, action, activeKeymap, window)
    }
  }

  private fun register(intention: IntentionAction) {
    if (findIntentionWrapper(intention) == null) {
      ActionManager.getInstance().registerAction(intention.wrappedActionId, IntentionActionAsAction(intention))
    }
  }

  private fun unregister(intention: IntentionAction) {
    ActionManager.getInstance().unregisterAction(intention.wrappedActionId)
  }

  private fun findIntentionWrapper(intention: IntentionAction): AnAction? =
    ActionManager.getInstance().getAction(intention.wrappedActionId)

  internal fun findIntention(wrappedActionId: String): IntentionAction? =
    IntentionManager.getInstance().availableIntentions.firstOrNull { it.wrappedActionId == wrappedActionId }

   /** Register all intentions with shortcuts and keep them up to date */
  private fun registerIntentionsInActiveKeymap() {
    registerIntentionsInKeymap(KeymapManager.getInstance().activeKeymap)

    val busConnection = ApplicationManager.getApplication().messageBus.connect(this)
    busConnection.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
      override fun activeKeymapChanged(keymap: Keymap?) {
        unregisterIntentionsInKeymap()
        registerIntentionsInKeymap(keymap)
      }
    })

    IntentionManagerImpl.EP_INTENTION_ACTIONS.addExtensionPointListener(object : ExtensionPointListener<IntentionActionBean> {
      override fun extensionAdded(extension: IntentionActionBean, pluginDescriptor: PluginDescriptor) {
        registerIntentionsInKeymap(registeredKeymap)
      }

      override fun extensionRemoved(extension: IntentionActionBean, pluginDescriptor: PluginDescriptor) {
        unregisterIntentionsInExtension(extension)
      }
    }, this)
  }

  private fun registerIntentionsInKeymap(keymap: Keymap?) {
    keymap?.wrappedIntentionActions()?.forEach { register(it) }
    registeredKeymap = keymap
  }

  private fun unregisterIntentionsInKeymap() {
    registeredKeymap?.wrappedIntentionActions()?.forEach { unregister(it) }
    registeredKeymap = null
  }

  private fun unregisterIntentionsInExtension(extension: IntentionActionBean) {
    registeredKeymap?.wrappedIntentionActions()?.forEach { intention ->
      if (intention is IntentionActionWrapper && intention.implementationClassName == extension.className) {
        unregister(intention)
      }
    }
  }

  /** Collect all intentions with assigned shortcuts in a keymap */
  private fun Keymap.wrappedIntentionActions(): Sequence<IntentionAction> {
    val intentionsById = IntentionManager.getInstance().intentionActions.associateBy { it.wrappedActionId }
    return actionIds
      .asSequence()
      .filter { it.startsWith(WRAPPER_PREFIX) }
      .mapNotNull { intentionsById[it] }
  }

  override fun dispose() {}

  class InitListener : ApplicationInitializedListener {
    override fun componentsInitialized() {
      getInstance().registerIntentionsInActiveKeymap()
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): IntentionShortcutManager = service()
  }
}

