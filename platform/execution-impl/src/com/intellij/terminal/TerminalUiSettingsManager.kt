// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal

import com.intellij.application.options.editor.EditorOptionsListener
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.util.Disposer
import com.jediterm.terminal.emulator.ColorPalette
import java.awt.Color
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.APP)
class TerminalUiSettingsManager internal constructor() : Disposable {
  var editorColorsScheme: EditorColorsScheme
    private set
  private var cachedColorPalette: JBTerminalSchemeColorPalette? = null
  private var fontSize = -1
  private val terminalPanels: MutableList<JBTerminalPanel> = CopyOnWriteArrayList()

  init {
    editorColorsScheme = EditorColorsManager.getInstance().globalScheme
    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(UISettingsListener.TOPIC, UISettingsListener {
      resetFontSize() // Enter/Exit Presentation Mode
    })
    connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { scheme ->
      // ANSI colors changed, Console Font changed
      editorColorsScheme = scheme ?: EditorColorsManager.getInstance().globalScheme
      cachedColorPalette = null
      resetFontSize()
    })
    connection.subscribe(EditorOptionsListener.APPEARANCE_CONFIGURABLE_TOPIC, EditorOptionsListener {
      fireCursorUpdate() // update on "Caret blinking" changes in "Editor | General | Appearance"
    })
  }

  fun getTerminalColorPalette(): ColorPalette {
    var palette = cachedColorPalette
    if (palette == null) {
      palette = JBTerminalSchemeColorPalette(editorColorsScheme)
      cachedColorPalette = palette
    }
    return palette
  }

  fun getDefaultForeground(): Color {
    val foregroundColor = editorColorsScheme.getAttributes(ConsoleViewContentType.NORMAL_OUTPUT_KEY).foregroundColor
    return foregroundColor ?: editorColorsScheme.defaultForeground
  }

  fun getDefaultBackground(): Color {
    val backgroundColor = editorColorsScheme.getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY)
    return backgroundColor ?: editorColorsScheme.defaultBackground
  }

  @JvmName("addListener")
  internal fun addListener(terminalPanel: JBTerminalPanel) {
    terminalPanels.add(terminalPanel)
    Disposer.register(terminalPanel) { terminalPanels.remove(terminalPanel) }
  }

  fun fireCursorUpdate() {
    for (panel in terminalPanels) {
      panel.setCursorShape(panel.settingsProvider.cursorShape)
      panel.repaint()
    }
  }

  private fun fireFontChanged() {
    for (panel in terminalPanels) {
      panel.fontChanged()
    }
  }

  fun getFontSize() : Int {
    if (fontSize <= 0) {
      fontSize = detectFontSize()
    }
    return fontSize
  }

  fun setFontSize(newFontSize: Int) {
    val prevFontSize = fontSize
    fontSize = newFontSize
    if (prevFontSize != fontSize) {
      fireFontChanged()
    }
  }

  private fun detectFontSize(): Int {
    return if (UISettings.instance.presentationMode) {
      UISettings.instance.presentationModeFontSize
    }
    else editorColorsScheme.consoleFontSize
  }

  fun resetFontSize() {
    setFontSize(detectFontSize())
  }

  override fun dispose() {}

  companion object {
    @JvmStatic
    fun getInstance(): TerminalUiSettingsManager = service()
  }
}