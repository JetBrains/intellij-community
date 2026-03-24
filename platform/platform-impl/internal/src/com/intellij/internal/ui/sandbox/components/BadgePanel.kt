// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.Badge
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class BadgePanel : UISandboxPanel {

  override val title: String = "Badge"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group("Predefined Badges") {
        row {
          for (badge in listOf(Badge.new, Badge.alpha, Badge.beta, Badge.trial)) {
            cell(JLabel(badge))
          }
        }
        row {
          for (badge in listOf(Badge.newDisabled, Badge.alphaDisabled, Badge.betaDisabled, Badge.trialDisabled)) {
            cell(JLabel(badge))
          }
        }
      }

      group("All Color Types") {
        row {
          for (badge in allColorTypeBadges) {
            cell(JLabel(badge))
          }
        }
      }

      group("Badges in the List") {
        row {
          val renderer = listCellRenderer<Badge> {
            text("Item $index")
            icon(value)
          }

          cell(JBList(allColorTypeBadges)).applyToComponent {
            cellRenderer = renderer
          }
        }
      }

      group("Tab with Badges") {
        row {
          val tabs = JBTabsImpl(project = null, disposable)

          tabs.addTab(createTabContent("Regular tab content"), "Settings")
          tabs.addTab(createTabContent("Beta feature content"), "Feature", Badge.beta)
          tabs.addTab(createTabContent("New feature content"), "New Feature", Badge.new)

          val wrapper = Wrapper(tabs)
          wrapper.border = JBUI.Borders.customLine(JBColor.border())
          wrapper.preferredSize = JBDimension(100, 100)
          cell(wrapper).align(AlignX.FILL)
        }
      }
    }
  }

  private fun JBTabsImpl.addTab(content: JComponent, text: String, icon: Icon? = null) {
    val tabInfo = TabInfo(content)
      .setText(text)
      .setIcon(icon)
    addTab(tabInfo)

    if (icon != null) {
      val tabLabel = getTabLabel(tabInfo)?.labelComponent as? SimpleColoredComponent ?: return
      tabLabel.isIconOnTheRight = true
    }
  }

  private fun createTabContent(text: String): JComponent {
    val content = JPanel(BorderLayout())
    content.add(JLabel(text), BorderLayout.CENTER)
    return content
  }
}

private val allColorTypeBadges = listOf(
  Badge("Blue", Badge.ColorType.BLUE),
  Badge("Blue secondary", Badge.ColorType.BLUE_SECONDARY),
  Badge("Green", Badge.ColorType.GREEN),
  Badge("Green secondary", Badge.ColorType.GREEN_SECONDARY),
  Badge("Purple secondary", Badge.ColorType.PURPLE_SECONDARY),
  Badge("Gray secondary", Badge.ColorType.GRAY_SECONDARY),
  Badge("Disabled").apply { enabled = false },
)
