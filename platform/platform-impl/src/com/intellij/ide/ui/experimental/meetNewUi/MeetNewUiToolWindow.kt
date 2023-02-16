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
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.DEFAULT_STYLE_KEY
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.JBGaps
import com.intellij.ui.dsl.gridLayout.JBVerticalGaps
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JLabel

internal class MeetNewUiToolWindow(private val project: Project, private val toolWindow: ToolWindow)
  : SimpleToolWindowPanel(true, true), DataProvider {

  companion object {
    private const val BANNER_HEIGHT = 180
    private val BANNER = IconLoader.getIcon("expui/meetNewUi/banner.svg", MeetNewUiToolWindow::class.java.classLoader)
    private val LIGHT = IconLoader.getIcon("expui/meetNewUi/light.svg", MeetNewUiToolWindow::class.java.classLoader)
    private val DARK = IconLoader.getIcon("expui/meetNewUi/dark.svg", MeetNewUiToolWindow::class.java.classLoader)
    private val SYSTEM = IconLoader.getIcon("expui/meetNewUi/system.svg", MeetNewUiToolWindow::class.java.classLoader)
    private val CLEAN = IconLoader.getIcon("expui/meetNewUi/clean.svg", MeetNewUiToolWindow::class.java.classLoader)
    private val COMPACT = IconLoader.getIcon("expui/meetNewUi/compact.svg", MeetNewUiToolWindow::class.java.classLoader)
  }

  private val themeButtons = mutableMapOf<LafReference?, MeetNewUiButton>()
  private lateinit var cleanDensity: Density
  private lateinit var compactDensity: Density

  private val panel = panel {
    customizeSpacingConfiguration(EmptySpacingConfiguration()) {
      row {
        val scale = JBUI.scale(BANNER_HEIGHT).toFloat() / BANNER.iconHeight
        cell(JLabel(IconUtil.scale(BANNER, null, scale)))
      }
      panel {
        row {
          label(IdeBundle.message("meetnewui.toolwindow.title"))
            .applyToComponent {
              font = JBFont.h1()
            }
        }.customize(JBVerticalGaps(bottom = 24))
        row {
          label(IdeBundle.message("meetnewui.toolwindow.theme"))
        }.customize(JBVerticalGaps(bottom = 8))
        row {
          findLafReference("Light")?.let { lafReference ->
            theme(lafReference.toString(), LIGHT, lafReference)
              .customize(JBGaps(right = 8))
          }
          findLafReference("Dark")?.let { lafReference ->
            theme(lafReference.toString(), DARK, lafReference)
              .customize(JBGaps(right = 8))
          }
          theme(IdeBundle.message("meetnewui.toolwindow.system"), SYSTEM, null)

          cell() // Deny right component to shrink

          // todo add other themes?
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
          button(IdeBundle.message("meetnewui.toolwindow.button.starttour")) {
            Not implemented yet
          }.applyToComponent {
            putClientProperty(DEFAULT_STYLE_KEY, true)
          }.customize(JBGaps(right = 8))
          */

          button(IdeBundle.message("meetnewui.toolwindow.button.gotit")) {
            val toolWindowManager = (ToolWindowManagerEx.getInstanceEx(project) as ToolWindowManagerImpl)
            toolWindowManager.hideToolWindow(toolWindow.id, hideSide = true, removeFromStripe = true)
          }.applyToComponent {
            putClientProperty(DEFAULT_STYLE_KEY, true)
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

  /**
   * @param lafReference null for system
   */
  private fun Row.theme(@NlsContexts.Button text: String, icon: Icon, lafReference: LafReference?): Cell<MeetNewUiButton> {
    val button = MeetNewUiButton(text, icon).apply {
      putClientProperty(DslComponentProperty.VISUAL_PADDINGS, Gaps.EMPTY)
      selectionArc = JBUI.scale(32)
      addClickListener(Runnable {
        setLaf(lafReference)
      })
    }

    themeButtons[lafReference] = button
    return cell(button)
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

  private fun updateThemeSelection() {
    val lafManager = LafManager.getInstance()
    for ((lafReference, button) in themeButtons) {
      val selected = if (lafManager.autodetect) lafReference == null else lafManager.lookAndFeelReference == lafReference
      button.selected = selected
      button.font = if (selected) JBFont.regular().asBold() else JBFont.regular()
    }
  }

  private fun findLafReference(name: String): LafReference? {
    val lafManager = LafManager.getInstance()
    return lafManager.lafComboBoxModel.items.find { it.toString() == name }
  }

  private fun Row.density(icon: Icon, @Nls name: String, gaps: Gaps, compactMode: Boolean): Density {
    val button = MeetNewUiButton(icon = icon).apply {
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

private data class Density(val button: MeetNewUiButton, val label: JLabel) {

  fun setSelection(selected: Boolean) {
    button.selected = selected

    if (selected) {
      label.font = JBFont.medium().asBold()
      label.foreground = JBUI.CurrentTheme.Label.foreground()
    }
    else {
      label.font = JBFont.medium()
      label.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    }
  }
}