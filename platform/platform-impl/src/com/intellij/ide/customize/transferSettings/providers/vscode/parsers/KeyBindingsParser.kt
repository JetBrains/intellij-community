package com.intellij.ide.customize.transferSettings.providers.vscode.parsers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.ide.customize.transferSettings.models.KeyBinding
import com.intellij.ide.customize.transferSettings.models.PatchedKeymap
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.ide.customize.transferSettings.providers.vscode.mappings.KeyBindingsMappings
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.diagnostic.logger
import java.io.File
import javax.swing.KeyStroke

private val logger = logger<KeyBindingsParser>()

class KeyBindingsParser(private val settings: Settings) {
  companion object {
    private const val COMMAND = "command"
    private const val KEY = "key"
  }

  fun process(file: File) = try {
    logger.info("Processing a file: $file")

    val root = ObjectMapper().enable(JsonParser.Feature.ALLOW_COMMENTS).readTree(file) as? ArrayNode
               ?: error("Unexpected JSON data; expected: ${JsonNodeType.ARRAY}")

    processKeyBindings(root)
  }
  catch (t: Throwable) {
    logger.warn(t)
  }

  private fun processKeyBindings(root: ArrayNode) {
    root.elements()?.forEach {
      try {
        val binding = it as? ObjectNode ?: return@forEach

        val commandId = binding[COMMAND]?.textValue() ?: return@forEach
        val shortcuts = binding[KEY]?.textValue() ?: return@forEach

        addCustomShortcut(commandId, shortcuts)
      }
      catch (t: Throwable) {
        logger.warn(t)
      }
    }

    val t1 = toAdd.map { (k, v) -> KeyBinding(k, v) }
    val t2 = toRemove.map { (k, v) -> KeyBinding(k, v) }
    if (t1.isNotEmpty() || t2.isNotEmpty()) {
      val km = settings.keymap
      requireNotNull(km)
      settings.keymap = PatchedKeymap(km, t1, t2)
    }
  }

  private val toRemove = mutableMapOf<String, MutableList<KeyboardShortcut>>()
  private val toAdd = mutableMapOf<String, MutableList<KeyboardShortcut>>()

  private fun addCustomShortcut(foreignCommandId: String, foreignShortcuts: String) {
    val commandId = KeyBindingsMappings.commandIdMap(foreignCommandId) ?: return
    val shortcuts = try {
      parseShortcuts(foreignShortcuts)
    }
                    catch (t: Throwable) {
                      logger.warn("Could not parse $foreignCommandId: $foreignShortcuts")
                      null
                    } ?: return

    if (foreignCommandId.startsWith('-')) {
      toRemove.getOrPut(commandId) { mutableListOf() }
      toRemove[commandId]?.add(shortcuts)
    }
    else {
      toAdd.getOrPut(commandId) { mutableListOf() }
      toAdd[commandId]?.add(shortcuts)
    }
  }

  private fun parseShortcuts(str: String): KeyboardShortcut? {
    if (str.isEmpty()) return null

    val strokes = str.split(' ')

    val firstKeyStroke = getKeyStroke(strokes.getOrNull(0)) ?: return null
    val secondStrokeRaw = strokes.getOrNull(1)
    val secondKeyStroke = getKeyStroke(secondStrokeRaw)

    if (secondStrokeRaw != null && secondKeyStroke == null) {
      // This means that we got an incomplete shortcut and we don't want it
      return null
    }

    return KeyboardShortcut(firstKeyStroke, secondKeyStroke)
  }

  private fun getKeyStroke(s: String?): KeyStroke? {
    if (s.isNullOrEmpty()) {
      return null
    }

    val sb = StringBuilder()
    s.uppercase().split('+').forEach {
      val normalizedShortcut = KeyBindingsMappings.shortcutMap(it)
      sb.append("$normalizedShortcut ")
    }

    return KeyStroke.getKeyStroke(sb.dropLast(1).toString())
  }
}