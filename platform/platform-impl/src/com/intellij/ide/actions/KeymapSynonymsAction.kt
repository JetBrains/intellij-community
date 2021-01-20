// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.keymap.impl.NationalKeyStrokeUtils
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.getExtendedKeyCodeForChar

class KeymapSynonymsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {

    val synonymPreview = initEditor()

    val dialogue = dialog(
      title = "",
      panel = panel {
        row {
          label("Add synonyms for keys")
        }
        row {
          synonymPreview()
        }
      }
    )

    val validator = DataValidator(synonymPreview, dialogue)
    synonymPreview.addDocumentListener(object : DocumentListener {
      override fun documentChanged(e: DocumentEvent) {
        validator.validate()
      }
    })

    dialogue.showAndGet().let {
      val synonyms = validator.validate()
      if (it && synonyms != null) {
        NationalKeyStrokeUtils.setSynonymConfig(synonyms)
      }
    }
  }

  private fun generateText(synonyms: Map<Int, KeyWithMods>): String {
    return buildString {
      for ((k,v) in synonyms) {
        append(KeyEvent.getKeyText(k))
        append(" with ")
        if ((v.mods and KeyEvent.SHIFT_DOWN_MASK) == KeyEvent.SHIFT_DOWN_MASK) {
          append("Shift ")
        }
        if ((v.mods and KeyEvent.CTRL_DOWN_MASK) == KeyEvent.CTRL_DOWN_MASK) {
          append("Ctrl ")
        }
        if ((v.mods and KeyEvent.META_DOWN_MASK) == KeyEvent.META_DOWN_MASK) {
          if (SystemInfo.isMac) {
            append("Cmd ")
          } else {
            append("Meta ")
          }
        }
        if ((v.mods and KeyEvent.ALT_DOWN_MASK) == KeyEvent.ALT_DOWN_MASK) {
          append("Alt ")
        }
        append(KeyEvent.getKeyText(v.key))
        append("\n")
      }
    }
  }

  private fun initEditor(): EditorTextField {
    val text = generateText(NationalKeyStrokeUtils.getSynonymConfig())

    val document = EditorFactory.getInstance().createDocument(text)
    if (text.isEmpty()) {
      document.setText("/ to Shift 7")
    }
    val synonymPreview = EditorTextField(document, null, FileTypes.PLAIN_TEXT,
                                             false, false)
    synonymPreview.preferredSize = Dimension(500, 280)
    synonymPreview.addSettingsProvider { editor ->
      editor.setVerticalScrollbarVisible(true)
      editor.setHorizontalScrollbarVisible(true)
      editor.settings.additionalLinesCount = 2
    }

    return synonymPreview
  }
}

class DataValidator(private val synonymPreview: EditorTextField,
                    private val dialogue: DialogWrapper) {
  companion object {
    private val regex = Regex("\\s+")
    private fun removeDuplicateSpaces(s: String) = regex.replace(s, " ")
    private fun process(s: String) = removeDuplicateSpaces(s.trim().toUpperCase())
  }

  fun validate(): Map<Int, KeyWithMods>? {
    var isOk = true
    val synonyms = mutableMapOf<Int, KeyWithMods>()
    synonymPreview.text
      .split("\n")
      .forEachIndexed { index, s ->
        val processed = process(s)
        if (processed.isEmpty()) return@forEachIndexed
        try {
          val synonym = parseSynonym(processed)
          synonyms[synonym.first] = synonym.second
        } catch (e: RuntimeException) {
          isOk = false
          val editor = synonymPreview.editor ?: return@forEachIndexed
          HintManagerImpl.getInstanceImpl().showErrorHint(editor, e.message!!)
          editor.markupModel.addLineHighlighter(index,
                                                HighlighterLayer.ERROR,
                                                editor.colorsScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES))
          return@forEachIndexed
        }
      }
    dialogue.isOKActionEnabled = isOk
    if (isOk) {
      synonymPreview.editor?.markupModel?.removeAllHighlighters()
      return synonyms
    }
    return null
  }
}
typealias Synonym = Pair<Int, KeyWithMods>

data class KeyWithMods(val key: Int, val mods: Int)

fun parseSynonym(line: String): Synonym {
  val fromTo = line.split(" WITH ")
  if (fromTo.size != 2) {
    throw RuntimeException("Failed to parse: \"$line\"")
  }
  val from = fromTo[0]
  if (from.length != 1) {
    throw RuntimeException("Failed to parse key: \"$from\"")
  }
  val keyCode = getExtendedKeyCodeForChar(from[0].toInt())
  if (keyCode == KeyEvent.VK_UNDEFINED) {
    throw RuntimeException("Failed to parse key: \"$from\"")
  }

  val keyWithMods = parseKeyWithMods(fromTo[1])

  return keyCode to keyWithMods
}

// line should be StringProcessor.process(line)
fun parseKeyWithMods(to: String): KeyWithMods {
  val modsKeyCode = to.split(" ")
  if (modsKeyCode.isEmpty() || modsKeyCode.last().length != 1) {
    throw RuntimeException("Failed to parse: \"$to\"")
  }
  val keyCode = getExtendedKeyCodeForChar(modsKeyCode.last()[0].toInt())
  if (keyCode == KeyEvent.VK_UNDEFINED) {
    throw RuntimeException("Failed to parse key: \"${modsKeyCode.last()}\"")
  }

  val tokens = modsKeyCode.dropLast(1).toMutableSet()
  var mods = 0
  if (tokens.contains("SHIFT")) {
    mods = mods or KeyEvent.SHIFT_DOWN_MASK
    tokens.remove("SHIFT")
  }
  if (tokens.contains("CTRL")) {
    mods = mods or KeyEvent.CTRL_DOWN_MASK
    tokens.remove("CTRL")
  }
  if (tokens.contains("META")) {
    mods = mods or KeyEvent.META_DOWN_MASK
    tokens.remove("META")
  } else if (tokens.contains("CMD")) {
    mods = mods or KeyEvent.META_DOWN_MASK
    tokens.remove("CMD")
  }
  if (tokens.contains("ALT")) {
    mods = mods or KeyEvent.ALT_DOWN_MASK
    tokens.remove("ALT")
  }

  if (tokens.isEmpty()) {
    return KeyWithMods(keyCode, mods)
  }
  throw RuntimeException("Failed to parse: \"${tokens.joinToString(" ")}\"")
}