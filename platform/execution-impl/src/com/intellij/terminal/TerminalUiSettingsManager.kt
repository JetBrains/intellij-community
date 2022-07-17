// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.application.options.editor.EditorOptionsListener
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.util.Disposer
import com.jediterm.terminal.emulator.ColorPalette
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.util.concurrent.CopyOnWriteArrayList

@State(name = "TerminalUiSettingsManager", storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))])
@Service(Service.Level.APP)
class TerminalUiSettingsManager internal constructor() : PersistentStateComponent<TerminalUiSettingsManager.State>, Disposable {
  var editorColorsScheme: EditorColorsScheme
    private set
  private var cachedColorPalette: JBTerminalSchemeColorPalette? = null
  private var fontSize = -1f
  private val terminalPanels: MutableList<JBTerminalPanel> = CopyOnWriteArrayList()
  private var state = State()

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

  var cursorShape: CursorShape
    get() = state.cursorShape
    set(value) {
      state.cursorShape = value
      fireCursorUpdate()
    }

  private fun fireCursorUpdate() {
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

  fun getFontSize(): Int {
    return (getFontSize2D() + 0.5).toInt()
  }

  fun getFontSize2D(): Float {
    if (fontSize <= 0) {
      fontSize = detectFontSize()
    }
    return fontSize
  }

  fun setFontSize(newFontSize: Int) {
    setFontSize(newFontSize.toFloat())
  }

  fun setFontSize(newFontSize: Float) {
    val prevFontSize = fontSize
    fontSize = newFontSize
    if (prevFontSize != fontSize) {
      fireFontChanged()
    }
  }

  private fun detectFontSize(): Float {
    return if (UISettings.getInstance().presentationMode) {
      UISettings.getInstance().presentationModeFontSize.toFloat()
    }
    else editorColorsScheme.consoleFontSize2D
  }

  fun resetFontSize() {
    setFontSize(detectFontSize())
  }

  override fun dispose() {}

  companion object {
    @JvmStatic
    fun getInstance(): TerminalUiSettingsManager = service()
  }

  enum class CursorShape(val text: @Nls String) {
    BLOCK(IdeBundle.message("terminal.cursor.shape.block.name")),
    UNDERLINE(IdeBundle.message("terminal.cursor.shape.underline.name")),
    VERTICAL(IdeBundle.message("terminal.cursor.shape.vertical.name"))
  }

  class State {
    var cursorShape: CursorShape = CursorShape.BLOCK
  }

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
  }
}