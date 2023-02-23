// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.experimental.meetNewUi

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManager.LafReference
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.experimental.ExperimentalUiCollector
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.JBGaps
import com.intellij.ui.dsl.gridLayout.JBVerticalGaps
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.WrapLayout
import org.jetbrains.annotations.Nls
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel

internal class MeetNewUiToolWindow(private val project: Project, private val toolWindow: ToolWindow)
  : SimpleToolWindowPanel(true, true), DataProvider {

  companion object {
    internal val LOG = logger<MeetNewUiToolWindow>()

    //private const val BANNER_HEIGHT = 180
    private const val CUSTOM_THEME_INDEX = 0

    //private val BANNER = IconLoader.getIcon("expui/meetNewUi/banner.svg", MeetNewUiToolWindow::class.java.classLoader)
    private val LIGHT = IconLoader.getIcon("expui/meetNewUi/light.svg", MeetNewUiToolWindow::class.java.classLoader)
    private val DARK = IconLoader.getIcon("expui/meetNewUi/dark.svg", MeetNewUiToolWindow::class.java.classLoader)
    private val SYSTEM = IconLoader.getIcon("expui/meetNewUi/system.svg", MeetNewUiToolWindow::class.java.classLoader)
    private val CLEAN = IconLoader.getIcon("expui/meetNewUi/clean.svg", MeetNewUiToolWindow::class.java.classLoader)
    private val COMPACT = IconLoader.getIcon("expui/meetNewUi/compact.svg", MeetNewUiToolWindow::class.java.classLoader)
  }

  private val themes = mutableListOf<Theme>()
  private lateinit var cleanDensity: Density
  private lateinit var compactDensity: Density

  private val panel = panel {
    customizeSpacingConfiguration(EmptySpacingConfiguration()) {
      /*
      row {
        val scale = JBUI.scale(BANNER_HEIGHT).toFloat() / BANNER.iconHeight
        cell(JLabel(IconUtil.scale(BANNER, null, scale)))
      }
      */
      panel {
        row {
          label(IdeBundle.message("meetnewui.toolwindow.title"))
            .applyToComponent {
              font = JBFont.regular().biggerOn(7f).deriveFont(Font.PLAIN)
            }
        }.customize(JBVerticalGaps(bottom = 24))
        row {
          label(IdeBundle.message("meetnewui.toolwindow.theme"))
        }.customize(JBVerticalGaps(bottom = 8))
        row {
          themes += Theme(null, false, null)
          findLafReference("Light")?.let { lafReference ->
            themes += Theme(lafReference, false, LIGHT)
          }
          findLafReference("Dark")?.let { lafReference ->
            themes += Theme(lafReference, false, DARK)
          }
          themes += Theme(null, true, SYSTEM)

          val gap = JBUI.scale(8)
          val themesPanel = JPanel(WrapLayout(FlowLayout.LEADING, gap, gap))
          // Remove gaps around of the panel
          themesPanel.putClientProperty(DslComponentProperty.VISUAL_PADDINGS, JBGaps(gap, gap, gap, gap))
          for (theme in themes) {
            themesPanel.add(theme.button)
          }

          cell(themesPanel)
            .align(AlignX.FILL)
        }.customize(JBVerticalGaps(bottom = 20))
        row {
          label(IdeBundle.message("meetnewui.toolwindow.density"))
        }.customize(JBVerticalGaps(bottom = 8))
        row {
          cleanDensity = density(CLEAN, IdeBundle.message("meetnewui.toolwindow.clean"), JBGaps(right = 8), false)

          compactDensity = density(COMPACT, IdeBundle.message("meetnewui.toolwindow.compact"), Gaps.EMPTY, true)

          cell() // Deny right component to shrink
        }.customize(JBVerticalGaps(bottom = 20))
        row {
          comment(IdeBundle.message("meetnewui.toolwindow.description")) {
            ExperimentalUiCollector.logMeetNewUiAction(ExperimentalUiCollector.MeetNewUiAction.NEW_UI_LINK)
            ShowSettingsUtil.getInstance().showSettingsDialog(project, IdeBundle.message("configurable.new.ui.name"))
          }
        }.customize(JBVerticalGaps(bottom = 20))
        row {
          /*
          button(IdeBundle.message("meetnewui.toolwindow.button.startTour")) {
            Not implemented yet
          }.applyToComponent {
            putClientProperty(DEFAULT_STYLE_KEY, true)
          }.customize(JBGaps(right = 8))
          */

          button(IdeBundle.message("meetnewui.toolwindow.button.finishSetup")) {
            val toolWindowManager = (ToolWindowManagerEx.getInstanceEx(project) as ToolWindowManagerImpl)
            toolWindowManager.hideToolWindow(toolWindow.id, hideSide = true, removeFromStripe = true)
          }

          cell() // Deny right component to shrink
        }
      }.customize(JBGaps(32, 32, 32, 32))
    }
  }

  init {
    setContent(panel)
    updateThemeSelection()
    updateDensitySelection()

    val connection = ApplicationManager.getApplication().messageBus
      .connect(toolWindow.disposable)

    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { updateThemeSelection() })
    connection.subscribe(UISettingsListener.TOPIC, UISettingsListener { updateDensitySelection() })
  }


  private fun updateThemeSelection() {
    val lafManager = LafManager.getInstance()
    val currentLafReference = lafManager.lookAndFeelReference

    val exist = themes.any { it.lafReference == currentLafReference }
    if (!exist) {
      themes[CUSTOM_THEME_INDEX].lafReference = currentLafReference
    }

    for (theme in themes) {
      val selected = if (lafManager.autodetect) theme.system
      else {
        currentLafReference == theme.lafReference
      }
      theme.button.selected = selected
      theme.button.font = if (selected) JBFont.regular().deriveFont(Font.BOLD) else JBFont.regular()
    }
  }

  private fun findLafReference(name: String): LafReference? {
    val lafManager = LafManager.getInstance()
    return lafManager.lafComboBoxModel.items.find { it.toString() == name }
  }

  private fun Row.density(icon: Icon, @Nls name: String, gaps: Gaps, compactMode: Boolean): Density {
    val button = MeetNewUiButton(icon = icon).apply {
      border = null
      putClientProperty(DslComponentProperty.VISUAL_PADDINGS, Gaps.EMPTY)
      selectionArc = JBUI.scale(8)
      addClickListener {
        setDensity(compactMode)
      }
    }

    lateinit var label: JLabel
    this.panel {
      row {
        cell(button)
          .customize(JBGaps(bottom = 8))
      }

      row {
        label = label(name).component
      }
    }.customize(gaps)

    return Density(button, label)
  }

  private fun updateDensitySelection() {
    val compactMode = UISettings.getInstance().compactMode
    cleanDensity.setSelection(!compactMode)
    compactDensity.setSelection(compactMode)
  }

  private fun setDensity(compactMode: Boolean) {
    val uiSettings = UISettings.getInstance()

    if (uiSettings.compactMode != compactMode) {
      val densityAction = if (compactMode) ExperimentalUiCollector.MeetNewUiAction.DENSITY_COMPACT else ExperimentalUiCollector.MeetNewUiAction.DENSITY_CLEAN
      ExperimentalUiCollector.logMeetNewUiAction(densityAction)

      uiSettings.compactMode = compactMode
      LafManager.getInstance().applyDensity()
    }
  }
}

private class Theme(lafReference: LafReference?, val system: Boolean, icon: Icon?) {

  val button: MeetNewUiButton
  var lafReference: LafReference? = null
    set(value) {
      field = value
      button.isVisible = system || value != null
      button.text = if (system) IdeBundle.message("meetnewui.toolwindow.system") else value?.toString()
    }

  init {
    button = MeetNewUiButton(null, icon).apply {
      selectionArc = JBUI.scale(32)
      addClickListener(Runnable {
        val laf = this@Theme.lafReference
        if (!system && laf == null) {
          MeetNewUiToolWindow.LOG.error("lafReference is null")
        }
        else {
          setLaf(laf)
        }
      })
    }
    this.lafReference = lafReference
  }

  private fun setLaf(lafReference: LafReference?) {
    val lafManager = LafManager.getInstance()
    if (lafReference == null) {
      lafManager.autodetect = true
      ExperimentalUiCollector.logMeetNewUiTheme("System")
    }
    else {
      if (lafManager.autodetect) {
        lafManager.autodetect = false
      }
      ExperimentalUiCollector.logMeetNewUiTheme(lafReference.toString())
      QuickChangeLookAndFeel.switchLafAndUpdateUI(lafManager, lafManager.findLaf(lafReference), true)
    }
  }
}

private data class Density(val button: MeetNewUiButton, val label: JLabel) {

  fun setSelection(selected: Boolean) {
    button.selected = selected

    if (selected) {
      label.font = JBFont.medium().deriveFont(Font.BOLD)
      label.foreground = JBUI.CurrentTheme.Label.foreground()
    }
    else {
      label.font = JBFont.medium()
      label.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    }
  }
}
