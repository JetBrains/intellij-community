// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.experimental.meetNewUi

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.experimental.ExperimentalUiCollector
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.DEFAULT_STYLE_KEY
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.JBGaps
import com.intellij.ui.dsl.gridLayout.JBVerticalGaps
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import javax.swing.JLabel

internal class MeetNewUiToolWindow(private val project: Project, private val toolWindow: ToolWindow)
  : SimpleToolWindowPanel(true, true), DataProvider {

  companion object {
    private const val BANNER_HEIGHT = 180
    private val BANNER = IconLoader.getIcon("expui/meetNewUi/banner.svg", MeetNewUiToolWindow::class.java.classLoader)
  }

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
          button(IdeBundle.message("meetnewui.toolwindow.light")) {
            // todo
          }.customize(JBGaps(right = 8))
          button(IdeBundle.message("meetnewui.toolwindow.dark")) {
            // todo
          }.customize(JBGaps(right = 8))
          button(IdeBundle.message("meetnewui.toolwindow.system")) {
            // todo
          }
        }.customize(JBVerticalGaps(bottom = 20))
        row {
          label(IdeBundle.message("meetnewui.toolwindow.density"))
        }.customize(JBVerticalGaps(bottom = 8))
        row {
          button(IdeBundle.message("meetnewui.toolwindow.clean")) {
            // todo
          }.customize(JBGaps(right = 8))
          button(IdeBundle.message("meetnewui.toolwindow.compact")) {
            // todo
          }

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
  }
}
