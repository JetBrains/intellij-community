// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Internal

package com.intellij.internal.ui.sandbox.dsl.listCellRenderer

import com.intellij.icons.AllIcons
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.ListCellRenderer

internal class LcrListPanel : UISandboxPanel {

  override val title: String = "List"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      lateinit var enabled: JCheckBox
      row {
        enabled = checkBox("Enabled")
          .selected(true)
          .component
      }

      indent {
        row {
          jbList(listOf("Text", "With Icon", "Italic", "Commented"), listCellRenderer {
            toolTipText = value

            when (index) {
              0 -> text(value)
              1 -> {
                icon(AllIcons.General.Information)
                text(value)
              }
              2 -> text(value) {
                attributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, UIUtil.getLabelForeground())
              }
              3 -> {
                text(value) {
                  align = LcrInitParams.Align.LEFT
                }
                text("Comment") {
                  foreground = greyForeground
                }
              }
            }
          }).label("Mixed, tooltips", LabelPosition.TOP)

          jbList((1..99).map { "Item $it" }, textListCellRenderer { it })
          jbList((1..99).toList(), listCellRenderer {
            icon(if (index % 2 == 0) AllIcons.General.Add else AllIcons.General.Gear)
            text("Item $value")
          }).label("Icons", LabelPosition.TOP)

          val aligns = listOf(LcrInitParams.Align.LEFT, LcrInitParams.Align.CENTER, LcrInitParams.Align.RIGHT)
          jbList((1..99).toList(), listCellRenderer {
            val customAlign = aligns.getOrNull(index % (aligns.size + 1))
            text("$value: $customAlign") {
              align = customAlign
            }
          }).label("Align", LabelPosition.TOP)
            .align(Align.FILL)
        }

        row {
          val colors = listOf(UIUtil.getLabelForeground(),
                              JBColor.GREEN,
                              JBColor.MAGENTA)
          val styles = listOf(SimpleTextAttributes.STYLE_PLAIN,
                              SimpleTextAttributes.STYLE_BOLD,
                              SimpleTextAttributes.STYLE_ITALIC)

          jbList((1..99).toList(), listCellRenderer {
            val i = index % colors.size
            text("Item $value") {
              if (i > 0) {
                foreground = colors[i]
              }
            }
          }).label("Foreground", LabelPosition.TOP)

          jbList((1..99).toList(), listCellRenderer {
            val i = index % colors.size
            text("Item $value") {
              attributes = SimpleTextAttributes(styles[i], colors[i])
            }
          }).label("Attributes", LabelPosition.TOP)

          jbList((1..99).toList(), listCellRenderer {
            val i = index % colors.size
            if (i > 0) {
              background = colors[i]
            }
            text("Item $value")
          }).label("Background", LabelPosition.TOP)
        }

        row {
          @Suppress("UNCHECKED_CAST")
          val list = jbList((1..99).toList(), listCellRenderer {
            text("Item $value") {
              align = LcrInitParams.Align.LEFT
            }
            text("Not searchable text") {
              foreground = greyForeground
              speedSearchHighlighting = false
            }
          }).label("Speed search:", LabelPosition.TOP)
            .applyToComponent {
              minimumSize = JBDimension(100, 300)
            }
            .component.viewport.view as JBList<Int>
          TreeUIHelper.getInstance().installListSpeedSearch(list) {
            "Item $it"
          }
        }
      }.enabledIf(enabled.selected)
    }
  }
}

internal fun <T> Row.jbList(items: List<T>, renderer: ListCellRenderer<T>): Cell<JBScrollPane> {
  val list = JBList(items)
  list.setCellRenderer(renderer)
  val scroll = JBScrollPane(list)
  scroll.minimumSize = JBDimension(100, 200)
  scroll.isOverlappingScrollBar = true
  return cell(scroll)
}
