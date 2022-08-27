// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.sections

import com.intellij.icons.AllIcons
import com.intellij.ide.customize.transferSettings.models.DummyKeyboardShortcut
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.models.SettingsPreferencesKind
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.border.CompoundBorder

class KeymapSection(private val ideVersion: IdeVersion) : IdeRepresentationSection(ideVersion.settings.preferences, SettingsPreferencesKind.Keymap, AllIcons.Plugins.PluginLogo) {
  override val name = "Keymap"
  override val disabledCheckboxText = "Default IntelliJ keymap will be used"

  override fun worthShowing() = ideVersion.settings.keymap != null

  override fun getContent(): JComponent {
    val keymap = ideVersion.settings.keymap ?: error("Keymap is null, this is very wrong")
    return panel {
      keymap.demoShortcuts.take(3).forEach {
        row {
          val dsc = it.defaultShortcut
          if (dsc is KeyboardShortcut) cell(KeyboardTwoShortcuts(dsc, _isSelected)).customize(Gaps.EMPTY)
          if (dsc is DummyKeyboardShortcut) cell(KeyboardTwoShortcuts(dsc, _isSelected)).customize(Gaps.EMPTY)
          mutableLabel(it.humanName)
        }.layout(RowLayout.PARENT_GRID)
      }
    }
  }
}

private class KeyboardTwoShortcuts private constructor(private val shortcut: Pair<List<String>, List<String>?>, private val isSelected: AtomicBooleanProperty) : JPanel() {
  companion object {
    @Nls
    private val delim = if (SystemInfo.isMac) "" else "+"
    private val delimToParse = "+"

    private fun getKeystrokeText(accelerator: KeyStroke?): String {
      if (accelerator == null) return ""

      return if (SystemInfo.isMac)
        MacKeymapUtil.getKeyStrokeText(accelerator, delimToParse, true)
        else KeymapUtil.getKeystrokeText(accelerator)
    }

    private fun init(sc: KeyboardShortcut): Pair<List<String>, List<String>?> {
      return Pair(getKeystrokeText(sc.firstKeyStroke).split(delimToParse),
                  sc.secondKeyStroke?.let { getKeystrokeText(it).split(delimToParse) })
    }

    private fun init(sc: DummyKeyboardShortcut): Pair<List<String>, List<String>?> {
      return Pair(sc.firstKeyStroke.split(delimToParse), sc.secondKeyStroke?.split(delimToParse))
    }
  }

  init {
    layout = MigLayout("novisualpadding, ins 0, gap 0")
    parsePart(shortcut.first)
    shortcut.second?.let {
      add(JLabel(","), "gapleft 4, gapright 4")
      parsePart(it)
    }
  }

  constructor(sc: KeyboardShortcut, isSelected: AtomicBooleanProperty) : this(init(sc), isSelected)
  constructor(sc: DummyKeyboardShortcut, isSelected: AtomicBooleanProperty) : this(init(sc), isSelected)

  private fun parsePart(part: List<String>) {
    part.forEachIndexed { i, sc ->
      add(getKeyLabel(sc.trim()))
      if (i != part.size) {
        add(JLabel(delim), "gapleft 4, gapright 4")
      }
    }
  }

  private fun getKeyLabel(@Nls txt: String): JLabel {
    return JLabel(txt).apply {
      text = txt
      font = font.deriveFont(11.0f)
      border = CompoundBorder(
        RoundedLineBorder(JBColor.border(), 6, 1),
        JBUI.Borders.empty(2, 8)
      )

      isSelected.afterChange {
        foreground = if (it) UIUtil.getLabelForeground() else UIUtil.getLabelDisabledForeground()
      }
    }
  }
}