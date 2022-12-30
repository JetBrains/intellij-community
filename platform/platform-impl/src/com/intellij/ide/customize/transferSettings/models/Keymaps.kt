// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.models

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls

interface Keymap {
  val displayName: @Nls String
  val demoShortcuts: List<SimpleActionDescriptor>
}

class BundledKeymap(override val displayName: @Nls String, val keymap: Keymap, override val demoShortcuts: List<SimpleActionDescriptor>): com.intellij.ide.customize.transferSettings.models.Keymap {
  companion object {
    fun fromManager(keymapName: String, demoShortcuts: List<SimpleActionDescriptor>): BundledKeymap {
      val keymap = KeymapManagerEx.getInstanceEx().getKeymap(keymapName) ?: error("Keymap $keymapName was not found")

      return BundledKeymap(keymap.displayName, keymap, demoShortcuts)
    }

    fun fromManager(keymapName: String) = fromManager(keymapName, emptyList())
  }
}

class PluginKeymap(override val displayName: @Nls String, val pluginId: String, val installedName: String, val fallback: BundledKeymap, override val demoShortcuts: List<SimpleActionDescriptor>) : com.intellij.ide.customize.transferSettings.models.Keymap

class PatchedKeymap(var parent: com.intellij.ide.customize.transferSettings.models.Keymap, val overrides: List<KeyBinding>, val removal: List<KeyBinding>) : com.intellij.ide.customize.transferSettings.models.Keymap {
  override val displayName: String get() = parent.displayName
  override val demoShortcuts by lazy { mergeShortcutsForDemo() }

  private fun mergeShortcutsForDemo(): List<SimpleActionDescriptor> {
    val overrides = overrides.associate { it.toPair() }
    val removal = removal.associate { it.toPair() }
    val res = parent.demoShortcuts.mapNotNull {
      val newShortcuts = it.defaultShortcut as? KeyboardShortcut ?: return@mapNotNull it
      if (removal[it.action]?.contains(newShortcuts) == true) return@mapNotNull null
      val overridesSc = overrides[it.action]
      if (!overridesSc.isNullOrEmpty()) {
        SimpleActionDescriptor(it.action, it.humanName, overridesSc.random())
      }
      else {
        it
      }
    }
    return res
  }
}

data class KeyBinding(
  @NlsSafe val actionId: String,
  val shortcuts: List<KeyboardShortcut>
) {
  override fun toString(): String {
    return "$actionId: ${shortcuts}\n"
  }
  fun toPair(): Pair<String, List<KeyboardShortcut>> = actionId to shortcuts
}

class SimpleActionDescriptor(
  val action: String,
  @NlsContexts.Label val humanName: String,
  val defaultShortcut: Any // KeyboardShortcut or DummyKeyboardShortcut
) {
  companion object {
    private fun fromKeymap(keymap: Keymap, actionIds: List<String>): List<SimpleActionDescriptor> {
      return actionIds.map {
        SimpleActionDescriptor(
          it,
          ActionManager.getInstance().getAction(it).templateText ?: error("Action $it doesn't contain its name"),
          keymap.getShortcuts(it).filterIsInstance<KeyboardShortcut>()
        )
      }
    }

    fun fromManager(keymapName: String, actionIds: List<String>): List<SimpleActionDescriptor> {
      val keymap = KeymapManagerEx.getInstanceEx().getKeymap(keymapName) ?: error("Keymap was not found")

      return fromKeymap(keymap, actionIds)
    }
  }
}

