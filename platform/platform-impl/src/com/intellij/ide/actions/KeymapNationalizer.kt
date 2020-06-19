// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.util.containers.toArray
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.DefaultComboBoxModel
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.JLabel
import javax.swing.JTextArea
import javax.swing.KeyStroke

// TODO move out to external plugin
internal class KeymapNationalizer : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val someText = "Generate keymap support for"
    val replacementsText = "Replace"
    val keymapGenerator = KeymapGenerator()
    val values = keymapGenerator.supportedLocales.values.toArray(emptyArray())
    val replacementPreview = JTextArea(keymapGenerator.generateText())

    val inaccessibleLabel = JLabel(keymapGenerator.inaccessibleKeysLabel())

    dialog(
      title = "Generate national keymap",
      panel = panel {
        row {
          inaccessibleLabel()
        }
        row {
          label(someText)
          comboBox(DefaultComboBoxModel(values), keymapGenerator::chosenLang)
            .applyToComponent {
              addItemListener(ItemListener {
                if (it.stateChange == ItemEvent.SELECTED ) {
                  keymapGenerator.chosenLang = it.item as String
                  inaccessibleLabel.text = keymapGenerator.inaccessibleKeysLabel()
                  replacementPreview.text = keymapGenerator.generateText()
                }
              })
            }
        }
        row {
          label(replacementsText)
        }
        row {
          replacementPreview()
        }
      }
    ).showAndGet().let {
      if (it) {
        keymapGenerator.generateKeymap()
      }
    }
  }
}


class KeymapGenerator {
  val supportedLocales = mapOf("de" to "Deutsch",
                               "it" to "Italian",
                               "cz" to "Czech",
                               "ot" to "Other")
  //"fr" to "French",
  //"no" to "Norwegian",

  var chosenLang = "Deutsch"
  val deutchReplacement = mapOf(KeyEvent.VK_SEMICOLON to 1014, //ö
                                KeyEvent.VK_EQUALS to KeyEvent.VK_DEAD_GRAVE,
                                KeyEvent.VK_SLASH to KeyEvent.VK_MINUS,
                                KeyEvent.VK_DEAD_GRAVE to KeyEvent.VK_LESS,
                                KeyEvent.VK_OPEN_BRACKET to 1020, //ü
                                KeyEvent.VK_BACK_SLASH to KeyEvent.VK_NUMBER_SIGN, //#
                                KeyEvent.VK_CLOSE_BRACKET to KeyEvent.VK_PLUS,
                                KeyEvent.VK_QUOTE to 996) //ä

  val italianReplacement = mapOf(KeyEvent.VK_SEMICOLON to 0x10000f2, //ò
                                 KeyEvent.VK_EQUALS to 0x10000ec, //ì
                                 KeyEvent.VK_MINUS to KeyEvent.VK_QUOTE,
                                 KeyEvent.VK_SLASH to KeyEvent.VK_MINUS,
                                 KeyEvent.VK_DEAD_GRAVE to KeyEvent.VK_LESS,
                                 KeyEvent.VK_OPEN_BRACKET to 0x10000e8, //è
                                 KeyEvent.VK_BACK_SLASH to 0x10000f9, //ù
                                 KeyEvent.VK_CLOSE_BRACKET to KeyEvent.VK_PLUS,
                                 KeyEvent.VK_QUOTE to 0x10000e0) //à

  val czechReplacement = mapOf(KeyEvent.VK_SEMICOLON to KeyEvent.VK_SEMICOLON, //TODO ů
                               KeyEvent.VK_EQUALS to KeyEvent.VK_QUOTE, // '
                               KeyEvent.VK_MINUS to KeyEvent.VK_EQUALS,
                               KeyEvent.VK_SLASH to KeyEvent.VK_MINUS,
                               KeyEvent.VK_DEAD_GRAVE to KeyEvent.VK_SLASH,
                               KeyEvent.VK_OPEN_BRACKET to 0x10000fa, //ú
                               KeyEvent.VK_BACK_SLASH to KeyEvent.VK_BACK_SLASH, //TODO ¨
                               KeyEvent.VK_CLOSE_BRACKET to KeyEvent.VK_CLOSE_BRACKET, // TODO )
                               KeyEvent.VK_QUOTE to KeyEvent.VK_QUOTE) // TODO §

  val otherReplacement = mapOf(KeyEvent.VK_SEMICOLON to KeyEvent.VK_SEMICOLON,
                               KeyEvent.VK_EQUALS to KeyEvent.VK_EQUALS,
                               KeyEvent.VK_COMMA to KeyEvent.VK_COMMA,
                               KeyEvent.VK_MINUS to KeyEvent.VK_MINUS,
                               KeyEvent.VK_PERIOD to KeyEvent.VK_PERIOD,
                               KeyEvent.VK_SLASH to KeyEvent.VK_SLASH,
                               KeyEvent.VK_DEAD_GRAVE to KeyEvent.VK_DEAD_GRAVE,
                               KeyEvent.VK_OPEN_BRACKET to KeyEvent.VK_OPEN_BRACKET,
                               KeyEvent.VK_BACK_SLASH to KeyEvent.VK_BACK_SLASH,
                               KeyEvent.VK_CLOSE_BRACKET to KeyEvent.VK_CLOSE_BRACKET,
                               KeyEvent.VK_QUOTE to KeyEvent.VK_QUOTE)

  fun isSupportedLocale(): Boolean {
    // FIXME detect locale
    val locale = KeyboardFocusManager.getCurrentKeyboardFocusManager()?.focusOwner?.inputContext?.locale
    println(locale)
    if (locale != null) {
      return supportedLocales.contains(locale.language)
    }
    return false
  }

  fun inaccessibleKeysLabel(): String {
    return "<html>Your keyboard is missing these primary keys: " + getInaccessibleKeys() + "</html>"
  }

  fun getInaccessibleKeys(): String {
    return getReplacements().keys.map { keyToText(it) }.joinToString("</b> <b>", "<b>", "</b>")
  }

  fun getReplacements(): Map<Int, Int> {
    return when (chosenLang) {
      "Deutch" -> deutchReplacement
      "Italian" -> italianReplacement
      "Czech" -> czechReplacement
      else -> otherReplacement
    }
  }

  fun keyToText(key: Int): String {
    if (key == KeyEvent.VK_DEAD_GRAVE) {
      return "`"
    }
    return KeymapUtil.getKeyText(key)
  }

  fun generateText(): String {
    val s = StringBuilder()

    for (r in getReplacements()) {
      s.append("${keyToText(r.key)} with ${keyToText(r.value)}\n")
    }

    return s.toString()
  }

  fun generateKeymap() {
    val replacements = getReplacements()

    val keymapManager = KeymapManager.getInstance()
    val activeKeymap = keymapManager.activeKeymap
    val nationalKeymap = activeKeymap.deriveKeymap(activeKeymap.name + " with national support")

    for (actionId in nationalKeymap.actionIdList) {
      for (shortcut in nationalKeymap.getShortcuts(actionId)) {
        if (shortcut !is KeyboardShortcut) {
          continue
        }

        var shouldMerge = replacements.containsKey(shortcut.firstKeyStroke.keyCode)
        shouldMerge = shouldMerge or replacements.containsKey(shortcut.secondKeyStroke?.keyCode)

        if (shouldMerge) {
          val merged = merge(shortcut, replacements)
          nationalKeymap.removeShortcut(actionId, shortcut)
          nationalKeymap.addShortcut(actionId, merged)
        }
      }
    }
    (keymapManager as KeymapManagerEx?)?.schemeManager?.addScheme(nationalKeymap)
    (keymapManager as KeymapManagerEx).activeKeymap = nationalKeymap
  }

  fun merge(shortcut: KeyboardShortcut, replacements: Map<Int, Int>): KeyboardShortcut {
    if (shortcut.secondKeyStroke == null) {
      return KeyboardShortcut(merge(shortcut.firstKeyStroke, replacements), null)
    }
    return KeyboardShortcut(merge(shortcut.firstKeyStroke, replacements),
                            merge(shortcut.secondKeyStroke!!, replacements))
  }

  fun merge(stroke: KeyStroke, replacements: Map<Int, Int>): KeyStroke {
    val replacement = replacements[stroke.keyCode]
    if (replacement == null) {
      return stroke
    }
    return KeyStroke.getKeyStroke(replacement, stroke.modifiers, stroke.isOnKeyRelease)
  }
}
