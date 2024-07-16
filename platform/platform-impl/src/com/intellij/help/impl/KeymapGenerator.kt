// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.help.impl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.util.text.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<KeymapGenerator>()

private val LEVELS = (1..3).map { i ->
  " ".repeat(i * 2)
}.toList()

private class KeymapGenerator : ModernApplicationStarter() {
  override suspend fun start(args: List<String>) {
    val xml = StringBuilder()
    xml.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n<Keymaps>\n")

    val keyManager = KeymapManagerEx.getInstanceEx()
    val actionManager = serviceAsync<ActionManager>() as ActionManagerEx
    val boundActions = actionManager.getBoundActions()

    for (keymap in keyManager.allKeymaps) {
      xml.append(LEVELS[0]).append("<Keymap name=\"").append(keymap.presentableName).append("\">\n")

      val alreadyMapped = HashSet(keymap.actionIdList)
      for (id in alreadyMapped) {
        renderAction(id = id, asId = null, shortcuts = keymap.getShortcuts(id), dest = xml, actionManager = actionManager)
      }

      // we need to inject bound actions under their real names in every keymap that doesn't already have them
      for (id in boundActions) {
        val binding = actionManager.getActionBinding(id)
        if (binding != null && !alreadyMapped.contains(id)) {
          renderAction(id = binding, asId = id, shortcuts = keymap.getShortcuts(binding), dest = xml, actionManager = actionManager)
        }
      }

      xml.append(LEVELS[0]).append("</Keymap>\n")
    }
    xml.append("</Keymaps>")

    val targetFilePath = Path.of(if (args.size > 1) args[1] else PathManager.getHomePath())
      .resolve("keymap-%s.xml".format(ApplicationInfoEx.getInstanceEx().apiVersionAsNumber.productCode.lowercase()))

    try {
      withContext(Dispatchers.IO) {
        Files.createDirectories(targetFilePath.parent)
        Files.writeString(targetFilePath, xml)
      }
      LOG.info("Keymaps saved to: $targetFilePath")
    }
    catch (e: IOException) {
      LOG.error("Cannot save keymaps", e)
      ApplicationManagerEx.getApplicationEx().exit( /*force: */ false, /*confirm: */ true, -1)
    }
    ApplicationManagerEx.getApplicationEx().exit( /*force: */ false, /*confirm: */ true)
  }
}

private fun renderAction(
  id: String,
  asId: String?,
  shortcuts: Array<Shortcut>,
  dest: StringBuilder,
  actionManager: ActionManagerEx,
) {
  if (shortcuts.isEmpty()) {
    return
  }

  dest.append(LEVELS[1]).append("<Action id=\"").append(asId ?: id).append("\">\n")

  // Different shortcuts may have equal display strings (e.g., shift+minus and shift+subtract)
  // We don't want them do be duplicated for users
  shortcuts
    .asSequence()
    .map { KeymapUtil.getShortcutText(it) }
    .distinct()
    .forEach { shortcut ->
      dest.append(LEVELS[2]).append("<Shortcut>").append(shortcut).append("</Shortcut>\n")
    }

  val action = actionManager.getAction(id)
  if (action != null) {
    val text = action.templatePresentation.text
    if (text != null) {
      dest.append(LEVELS[2]).append("<Text>").append(Strings.escapeXmlEntities(text)).append("</Text>\n")
    }
  }
  dest.append(LEVELS[1]).append("</Action>\n")
}
