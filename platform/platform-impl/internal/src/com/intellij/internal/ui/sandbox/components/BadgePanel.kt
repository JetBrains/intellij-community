// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.icons.AllIcons
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.components.BadgeColorType
import com.intellij.ui.components.Badge
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBDimension
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class BadgePanel : UISandboxPanel {

  override val title: String = "Badge"

  private fun wrapBadgeIcon(icon: Badge): JComponent {
    val label = JLabel(icon)
    val panel = NonOpaquePanel(GridBagLayout())
    panel.add(label)
    return panel
  }

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group("Predefined Badges") {
        row {
          cell(JLabel(Badge.newBadge()))
          cell(JLabel(Badge.betaBadge()))
          cell(JLabel(Badge.freeBadge()))
          cell(JLabel(Badge.trialBadge()))
        }
      }

      group("All Color Types") {
        row {
          cell(JLabel(Badge("Blue", BadgeColorType.BLUE)))
          cell(JLabel(Badge("Blue Secondary (Default)")))
          cell(JLabel(Badge("Green", BadgeColorType.GREEN)))
          cell(JLabel(Badge("Green Secondary", BadgeColorType.GREEN_SECONDARY)))
          cell(JLabel(Badge("Purple Secondary", BadgeColorType.PURPLE_SECONDARY)))
          cell(JLabel(Badge("Gray Secondary", BadgeColorType.GRAY_SECONDARY)))
          cell(JLabel(Badge("Disabled", disabled = true)))
        }
      }

      group("Popup Item with Badge") {
        row {
          val items = listOf("Open Recent", "New File", "AI Assistant", "Settings", "Worktrees")
          val renderer = listCellRenderer<String> {
            icon(AllIcons.Stub)
            text(value)
            if (value == "AI Assistant") {
              icon(Badge.betaBadge())
            }
            if (value == "Worktrees") {
              icon(Badge.newBadge())
            }
          }

          val list = JBList(items)
          list.setCellRenderer(renderer)
          val scroll = JBScrollPane(list)
          scroll.preferredSize = JBDimension(250, list.preferredSize.height + 4)
          scroll.isOverlappingScrollBar = true
          cell(scroll).align(AlignY.TOP)
        }
      }

      group("Tab with Badge") {
        row {
          val tabs = JBTabsImpl(project = null, disposable)

          tabs.addTab(TabInfo(createTabContent("Regular tab content")).setText("Settings"))

          val featureTab = TabInfo(createTabContent("Beta feature content")).setText("Feature")
          tabs.addTab(featureTab)
          tabs.getTabLabel(featureTab)?.add(wrapBadgeIcon(Badge.betaBadge()), BorderLayout.EAST)

          val newFeatureTab = TabInfo(createTabContent("New feature content")).setText("New Feature")
          tabs.addTab(newFeatureTab)
          tabs.getTabLabel(newFeatureTab)?.add(wrapBadgeIcon(Badge.newBadge()), BorderLayout.EAST)

          val wrapper = Wrapper(tabs)
          wrapper.border = JBUI.Borders.customLine(JBColor.border())
          cell(wrapper).align(Align.FILL)
        }
      }
    }
  }

  private fun createTabContent(text: String): JComponent {
    val content = JPanel(BorderLayout())
    content.add(JLabel(text), BorderLayout.CENTER)
    content.preferredSize = Dimension(content.preferredSize.width, JBUI.scale(100))
    return content
  }
}
